(ns top.kzre.krro.plugin.painting.core.tool.brush
  "画笔工具：完全基于 tiled‑canvas。
   预览采用全量事件 + RDP 降采样，保证平滑连续的笔触。
   适配 ITool 新协议，所有 runtime 变更通过返回值传递。
   仅保留 undo 记录作为外部副作用。"
  (:require
    [top.kzre.krro.brush.core :as brush-core]
    [top.kzre.krro.brush.rdp :as rdp]
    [top.kzre.krro.brush.taper :as taper]
    [top.kzre.krro.canvas.core.layer.util :as layer-util]
    [top.kzre.krro.core.custom :as custom]
    [top.kzre.krro.plugin.painting.core.brush.core :as brush]
    [top.kzre.krro.plugin.painting.core.ops.layer :as layer]
    [top.kzre.krro.plugin.painting.core.ops.undo :as undo]
    [top.kzre.krro.plugin.painting.core.tool.protocol :as tp]
    [top.kzre.krro.plugin.undo.protocol]
    [top.kzre.krro.util.tiled-canvas :as tcanvas])
  (:import
    (top.kzre.krro.canvas.core.layer MathUtils)))

;; ── 自定义配置 ──────────────────────────────────
(custom/defcustom :krro.painting/rdp-epsilon
                  0.5
                  :type :number
                  :group :krro.painting/edit
                  :doc "RDP 降采样距离阈值（像素）。值越小保留的细节越多，越大简化越激进。")

;; ── 内部状态 ─────────────────────────────────────
(defrecord BrushState
  [new-events      ;; 自上次预览以来累积的事件（预览后清空）
   all-events      ;; 整个笔画的全部事件
   ^double stroke-length])

(defn make-state []
  (->BrushState [] [] 0.0))

(defn push-event [^BrushState st event]
  (let [old-all    (:all-events st)
        last-event (peek old-all)
        new-all    (conj old-all event)
        new-new    (conj (:new-events st) event)
        dist       (if last-event
                     (Math/sqrt (+ (Math/pow (- (:x event) (:x last-event)) 2)
                                   (Math/pow (- (:y event) (:y last-event)) 2)))
                     0.0)]
    (assoc st
      :new-events new-new
      :all-events new-all
      :stroke-length (+ (:stroke-length st) dist))))

(defn push-event!
  [state-atom event]
  (swap! state-atom push-event event))

;; ── 笔刷配置 ─────────────────────────────────────
(defn- get-brush []
  (or @brush/global-brush brush/default-brush))

;; ── 预览绘制（全量事件 + RDP） ─────────────────
(defn- draw-preview!
  [canvas brush all-events stroke-length epsilon]
  (when (seq all-events)
    (let [brush-no-smooth (assoc brush :smooth {:stabilizer :default})
          simplified (rdp/simplify all-events epsilon
                                   :preserve-head 50)
          stroke     (brush-core/events->stroke brush-no-smooth simplified
                                                (:spacing brush) (:radius brush))
          tapered    (taper/taper-stroke-start stroke (:taper-start brush)
                                               :fields [:radius :opacity]
                                               :end-dist stroke-length)]
      (brush-core/render-stroke-dirties! canvas tapered))))

;; ── 提交绘制（全量事件 + RDP） ─────────────────
(defn- commit-stroke!
  "返回 {:updated-canvas :dirties :new-backup}"
  [backup-canvas layer-canvas brush all-events stroke-length epsilon]
  (when (seq all-events)
    (let [simplified (rdp/simplify all-events epsilon
                                   :preserve-head 50
                                   :preserve-tail 50)
          stroke     (brush-core/events->stroke brush simplified
                                                (:spacing brush) (:radius brush))
          tapered    (taper/taper-stroke stroke (:taper-start brush) (:taper-end brush)
                                         :fields [:radius :opacity]
                                         :end-dist stroke-length)
          [new-canvas dirties] (brush-core/render-stroke-dirties! backup-canvas tapered)
          updated (tcanvas/copy-to! layer-canvas new-canvas)]
      {:updated-canvas updated
       :dirties        dirties
       :new-backup     new-canvas})))

;; ── 坐标转换 ──────────────────────────────────────
(defn- compute-total-inverse [layer layers layer-path]
  (let [inv-local (layer-util/compose-inverse-transform layer)
        inv-parent (layer-util/parent-inverse-transform layers layer-path)]
    (if inv-parent
      (MathUtils/multiply (float-array inv-local) (float-array inv-parent))
      (float-array inv-local))))

;; ═══════════════════════════════════════════════════════
;; 工具实现（符合新协议）
;; ═══════════════════════════════════════════════════════
(defrecord BrushTool [state-atom parent-inv]
  tp/ITool
  (begin! [_ layer rt _ctx]
    (reset! parent-inv nil)
    {:layer layer :state rt})

  (end! [_ layer rt _ctx]
    (reset! parent-inv nil)
    {:layer layer :state rt})

  (apply! [_ layer _rt ev ctx]
    (let [local-ev (if-let [inv-matrix @parent-inv]
                     (let [pt (layer-util/transform-point inv-matrix (:x ev) (:y ev))]
                       (assoc ev :x (:x pt) :y (:y pt)))
                     (let [layers (:layers (:data ctx))
                           layer-path (layer-util/find-layer-path (:id layer) layers)
                           total-inv (compute-total-inverse layer layers layer-path)]
                       (reset! parent-inv total-inv)
                       (let [pt (layer-util/transform-point total-inv (:x ev) (:y ev))]
                         (assoc ev :x (:x pt) :y (:y pt)))))]
      (case (:type local-ev)
        :press   (do (reset! state-atom (make-state))
                     (push-event! state-atom local-ev)
                     :start)
        :drag    (do (push-event! state-atom local-ev)
                     :continue)
        :release (do (push-event! state-atom local-ev)
                     :no-replace)
        :idle)))

  (preview! [_ layer rt ctx]
    (let [st @state-atom
          epsilon (custom/get-custom :krro.painting/rdp-epsilon (:frame ctx))]
      (if (seq (:new-events st))
        (let [canvas (:canvas layer)
              clean-canvas (tcanvas/copy-to! canvas (:layer-backup rt))
              brush (get-brush)
              all-evs (:all-events st)
              [new-canvas dirties] (draw-preview! clean-canvas brush all-evs (:stroke-length st) epsilon)]
          ;; 清空增量事件，保留全量事件
          (swap! state-atom assoc :new-events [])
          {:layer (assoc layer :canvas new-canvas)
           :state (update rt :dirty-tiles into dirties)})
        {:layer layer :state rt})))

  (commit! [_ layer rt ctx]
    (let [st @state-atom
          all-evs (:all-events st)
          stroke-len (:stroke-length st)
          epsilon (custom/get-custom :krro.painting/rdp-epsilon (:frame ctx))]
      (if (seq all-evs)
        (let [canvas-id (:canvas-id ctx)
              layer-id (:id layer)
              layer-canvas (:canvas layer)
              backup-canvas (:layer-backup rt)
              tmp-canvas (tcanvas/deep-copy backup-canvas)
              brush (get-brush)
              {:keys [updated-canvas dirties new-backup]}
              (commit-stroke! backup-canvas layer-canvas brush all-evs stroke-len epsilon)
              new-layer (assoc layer :canvas updated-canvas)]
          (layer/replace-layer! canvas-id new-layer)
          (undo/record-raster-stroke! canvas-id layer-id tmp-canvas new-backup dirties)
          (reset! state-atom (make-state))
          (reset! parent-inv nil)
          {:layer new-layer
           :state (-> rt
                      (assoc :layer-backup new-backup)
                      (update :dirty-tiles into dirties))})
        {:layer layer :state rt}))))

(defn make-brush []
  (->BrushTool (atom (make-state)) (atom nil)))
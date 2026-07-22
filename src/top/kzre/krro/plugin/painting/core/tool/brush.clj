(ns top.kzre.krro.plugin.painting.core.tool.brush
  "画笔工具：完全基于 TiledCanvas。
   预览采用全量事件 + RDP 降采样，保证平滑连续的笔触。
   速度计算使用 CableFilter2D 滤波，消除抖动。
   适配 ITool 新协议，所有 runtime 变更通过返回值传递。"
  (:require
    [top.kzre.krro.brush.core :as brush-core]
    [top.kzre.krro.brush.rdp :as rdp]
    [top.kzre.krro.canvas.core.layer.util :as layer-util]
    [top.kzre.krro.core.custom :as custom]
    [top.kzre.krro.plugin.painting.core.brush.core :as brush]
    [top.kzre.krro.plugin.painting.core.ops.layer :as layer]
    [top.kzre.krro.plugin.painting.core.ops.undo :as undo]
    [top.kzre.krro.plugin.painting.core.tool.protocol :as tp]
    [top.kzre.krro.plugin.painting.core.tool.util :as tool-util]
    [top.kzre.krro.plugin.undo.protocol])
  (:import
    (top.kzre.smooth CableFilter2D)
    (top.kzre.krro.util.tile TiledCanvas)))

;; ── 自定义配置 ──────────────────────────────────
(custom/defcustom :krro.painting/rdp-epsilon
                  0.5
                  :type :number
                  :group :krro.painting/edit
                  :doc "RDP 降采样距离阈值（像素）。")

(custom/defcustom :krro.painting/max-preview-events
                  20
                  :type :integer
                  :group :krro.painting/edit
                  :doc "预览时处理的最大事件数。")

(custom/defcustom :krro.painting/speed-filter-alpha
                  0.3
                  :type :number
                  :group :krro.painting/edit
                  :doc "速度滤波平滑系数 (0~1)，值越大越平滑但延迟越大。")

;; ── 内部状态 ──────────────────────────────────
(defrecord BrushState
  [new-events      ;; 自上次预览以来累积的事件
   all-events])    ;; 整个笔画的全部事件

(defn make-state []
  (->BrushState [] []))

(defn push-event [^BrushState st event ^CableFilter2D filter filter-state]
  (let [old-all    (:all-events st)
        last-event (peek old-all)
        raw-speed  (if last-event
                     (let [dx (- (:x event) (:x last-event))
                           dy (- (:y event) (:y last-event))
                           dt (max 1 (- (:timestamp event) (:timestamp last-event 0)))]
                       (/ (Math/sqrt (+ (* dx dx) (* dy dy))) dt))
                     1.0)
        _ (.filter filter raw-speed 0.0 filter-state)
        smooth-speed (.getFilteredX filter-state)
        event'     (assoc event :velocity smooth-speed)
        new-all    (conj old-all event')
        new-new    (conj (:new-events st) event')]
    (assoc st
      :new-events new-new
      :all-events new-all)))

(defn push-event! [state-atom event filter filter-state]
  (swap! state-atom push-event event filter filter-state))

;; ── 笔刷配置 ──────────────────────────────────
(defn- get-brush []
  (or @brush/global-brush brush/default-brush))

;; ── 预览绘制 ──────────────────────────────────
(defn- draw-preview!
  [^TiledCanvas canvas brush events epsilon]
  (when (seq events)
    (let [simplified (rdp/simplify events epsilon)
          stroke (brush-core/events->stroke brush simplified :skip-smooth? true)]
      (brush-core/render-stroke-dirties! canvas stroke))))

;; ── 提交绘制 ──────────────────────────────────
(defn- commit-stroke!
  "基于备份画布生成笔触，并返回更新后的画布、脏瓦片集合及新备份。
   新架构下直接使用渲染后的新画布，无需再 copy-to 原图层画布。"
  [backup-canvas layer-canvas brush all-events]
  (when (seq all-events)
    (let [stroke     (brush-core/events->stroke brush all-events)
          [new-canvas dirties] (brush-core/render-stroke-dirties! backup-canvas stroke)]
      {:updated-canvas (.mergeCanvas layer-canvas new-canvas)
       :dirties        dirties
       :new-backup     new-canvas})))

;; ═══════════════════════════════════════════════════════
;; 工具实现（符合新协议）
;; ═══════════════════════════════════════════════════════
(defrecord BrushTool [state-atom parent-inv current-pos speed-filter speed-filter-state]
  tp/ITool
  (id [_] :brush)
  (overlay [_] {:type :circle, :radius 10})
  (begin! [_ layer rt _ctx]
    (reset! parent-inv nil)
    {:layer layer :state rt})
  (end! [_ layer rt _ctx]
    (reset! parent-inv nil)
    {:layer layer :state rt})

  (apply! [_ layer _rt ev ctx]
    (reset! current-pos {:x (:x ev) :y (:y ev)})
    (let [local-ev (if-let [inv-matrix @parent-inv]
                     (let [pt (layer-util/transform-point inv-matrix (:x ev) (:y ev))]
                       (assoc ev :x (:x pt) :y (:y pt)))
                     (let [layers (:layers (:data ctx))
                           layer-path (layer-util/find-layer-path (:id layer) layers)
                           total-inv (tool-util/compute-total-inverse layer layers layer-path)]
                       (reset! parent-inv total-inv)
                       (let [pt (layer-util/transform-point total-inv (:x ev) (:y ev))]
                         (assoc ev :x (:x pt) :y (:y pt)))))]
      (case (:type local-ev)
        :press   (do (reset! state-atom (make-state))
                     (push-event! state-atom local-ev speed-filter speed-filter-state)
                     :start)
        :drag    (do (push-event! state-atom local-ev speed-filter speed-filter-state)
                     :continue)
        :release (do (push-event! state-atom local-ev speed-filter speed-filter-state)
                     :no-replace)
        :move    :update
        :hover   :update)))

  (preview! [_ layer rt ctx]
    (let [st @state-atom
          max-preview-event-count (custom/get-custom :krro.painting/max-preview-events (:frame ctx))
          epsilon (custom/get-custom :krro.painting/rdp-epsilon (:frame ctx))]
      (if (seq (:new-events st))
        ;; 直接基于旧画布绘制.
        (let [canvas (:canvas layer)
              brush (get-brush)
              all-evs (:all-events st)
              recent-evs (if (> (count all-evs) max-preview-event-count)
                           (vec (take-last max-preview-event-count all-evs))
                           all-evs)
              [new-canvas dirties] (draw-preview! canvas brush recent-evs epsilon)
              merged-dirties (into (or (:dirty-tiles rt) #{}) dirties)]
          (swap! state-atom assoc :new-events [])
          {:layer (assoc layer :canvas new-canvas)
           :state (assoc rt :dirty-tiles merged-dirties)})
        {:layer layer :state rt})))

  (commit! [_ layer rt ctx]
    (let [st @state-atom
          all-evs (:all-events st)]
      (if (seq all-evs)
        (let [canvas-id (:canvas-id ctx)
              layer-id (:id layer)
              layer-canvas (:canvas layer)
              backup-canvas (:canvas (:layer-backup rt))
              ;; 使用 shareFrom 替代深拷贝，并记录 tmp-canvas 以供释放
              ^TiledCanvas tmp-canvas
              (doto (TiledCanvas. (.getTileSize backup-canvas)
                                  (.getDefaultPixel backup-canvas))
                (.shareFrom backup-canvas))
              brush (get-brush)
              {:keys [updated-canvas dirties new-backup]}
              (commit-stroke! backup-canvas layer-canvas brush all-evs)
              merged-dirties (into (or (:dirty-tiles rt) #{}) dirties)
              new-layer (assoc layer :canvas updated-canvas)]
          (layer/replace-layer! canvas-id new-layer)
          (undo/record-raster-stroke! canvas-id layer-id tmp-canvas new-backup dirties)
          ;; 撤销记录完成后立即释放 tmp-canvas（因为快照已序列化或丢弃）
          (.clear tmp-canvas)
          (reset! state-atom (make-state))
          (reset! parent-inv nil)
          {:layer new-layer
           :state (assoc rt
                    :layer-backup {:type :raster
                                   :canvas new-backup}
                    :dirty-tiles merged-dirties)})
        {:layer layer :state rt}))))

(defn make-brush []
  (let [alpha (double (or (custom/get-custom :krro.painting/speed-filter-alpha) 0.3))
        filter (CableFilter2D/instance)
        state  (CableFilter2D/newState alpha)]
    (->BrushTool (atom (make-state)) (atom nil) (atom nil) filter state)))
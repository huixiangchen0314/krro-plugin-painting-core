(ns top.kzre.krro.plugin.painting.core.tool.brush
  "画笔工具：完全基于 tiled-canvas。适应 brush-core 新的 [new-canvas, dirties] 返回格式。"
  (:require
    [top.kzre.krro.brush.core :as brush-core]
    [top.kzre.krro.brush.taper :as taper]
    [top.kzre.krro.util.tiled-canvas :as tcanvas]
    [top.kzre.krro.plugin.painting.core.brush.core :as brush]
    [top.kzre.krro.plugin.painting.core.state :as state]
    [top.kzre.krro.plugin.painting.core.ops.undo :as undo]
    [top.kzre.krro.plugin.painting.core.tool.protocol :as tp]
    [top.kzre.krro.plugin.undo.protocol]
    [top.kzre.krro.canvas.core.layer.util :as layer-util])
  (:import
    (top.kzre.krro.canvas.core.layer MathUtils)))

;; ── 内部状态 ─────────────────────────────────────
(defrecord BrushState
  [new-events      ;; 自上次 preview! 以来累积的事件
   all-events      ;; 整个笔画的全部事件
   ^double stroke-length])

(defn make-state
  "创建空的画笔状态。"
  []
  (->BrushState [] [] 0.0))

;; ── 事件累积 ─────────────────────────────────────
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
  "在原子中更新状态，添加一个事件。"
  [state-atom event]
  (swap! state-atom push-event event))

;; ── 事件窗口控制 ──────────────────────────────────
(def ^:private min-keep-distance 10.0)
(def ^:private max-keep-count 50)
(def ^:private min-keep-count 5)

(defn- compute-keep-start [evs]
  (let [cnt (count evs)]
    (if (<= cnt min-keep-count)
      0
      (loop [i (dec cnt)
             dist 0.0
             kept-num 1
             last-x (:x (nth evs i))
             last-y (:y (nth evs i))]
        (if (zero? i)
          0
          (let [prev-i (dec i)
                prev-x (:x (nth evs prev-i))
                prev-y (:y (nth evs prev-i))
                dx (- last-x prev-x)
                dy (- last-y prev-y)
                d (Math/sqrt (+ (* dx dx) (* dy dy)))
                new-dist (+ dist d)
                new-kept-num (inc kept-num)]
            (if (or (and (>= new-kept-num min-keep-count)
                         (>= new-dist min-keep-distance))
                    (>= new-kept-num max-keep-count))
              i
              (recur prev-i new-dist new-kept-num prev-x prev-y))))))))

(defn drain-new-events
  "纯函数，返回 [events, new-state]。保留末尾适量事件以保证帧间平滑。"
  [st]
  (let [evs (:new-events st)
        cnt (count evs)]
    (if (<= cnt min-keep-count)
      [evs st]
      (let [keep-start (compute-keep-start evs)
            keep-evs   (subvec evs keep-start)
            new-st     (assoc st :new-events keep-evs)]
        [evs new-st]))))

(defn drain-new-events!
  "副作用版本：取出当前帧事件并更新原子状态。返回事件向量。"
  [state-atom]
  (let [result (atom nil)]
    (swap! state-atom
           (fn [st]
             (let [[events new-st] (drain-new-events st)]
               (reset! result events)
               new-st)))
    @result))

;; ── 笔刷配置 ─────────────────────────────────────
(defn- get-brush []
  (or @brush/global-brush brush/default-brush))

;; ── 预览绘制 ─────────────────────────────────────
(defn- draw-preview!
  "在画布上绘制预览笔触，返回新的画布（因为渲染可能添加新瓦片）。"
  [canvas brush events stroke-length]
  (when (seq events)
    (let [stroke  (brush-core/events->stroke brush events
                                             (:spacing brush) (:radius brush))
          tapered (taper/taper-stroke-start stroke (:taper-start brush)
                                            :fields [:radius :opacity]
                                            :end-dist stroke-length)
          [new-canvas _] (brush-core/render-stroke! canvas tapered)]
      new-canvas)))

;; ── 提交绘制 ─────────────────────────────────────
(defn- commit-stroke!
  "直接在备份画布上渲染，生成新画布；然后通过 copy-to! 合并到图层画布。
   返回更新后的图层画布。"
  [backup-canvas layer-canvas brush all-events stroke-length canvas-id layer-id]
  (when (seq all-events)
    (let [old-canvas (tcanvas/deep-copy backup-canvas)   ; 撤销快照
          stroke       (brush-core/events->stroke brush all-events
                                                  (:spacing brush) (:radius brush))
          tapered      (taper/taper-stroke stroke (:taper-start brush) (:taper-end brush)
                                           :fields [:radius :opacity]
                                           :end-dist stroke-length)
          [new-canvas dirties] (brush-core/render-stroke-dirties! backup-canvas tapered)]
      ;; 更新 runtime 备份为本次绘制后的新画布
      (state/set-layer-backup! canvas-id new-canvas)
      ;; 记录撤销（快照为修改前的备份画布）
      (undo/record-raster-stroke! canvas-id layer-id old-canvas new-canvas dirties)
      ;; 将新画布内容合并到原图层画布（返回新 map，但复用 tile 数组）
      (tcanvas/copy-to! layer-canvas new-canvas))))

;; ── 坐标转换（不变） ──────────────────────────────
(defn- compute-total-inverse [layer layers layer-path]
  (let [inv-local (layer-util/compose-inverse-transform layer)
        inv-parent (layer-util/parent-inverse-transform layers layer-path)]
    (if inv-parent
      (MathUtils/multiply (float-array inv-local) (float-array inv-parent))
      (float-array inv-local))))

;; ── 工具实现 ──────────────────────────────────────
(defrecord BrushTool [state-atom parent-inv]
  tp/ITool
  (begin! [_ layer _ctx]
    (reset! parent-inv nil)
    layer)

  (end! [_ layer _ctx]
    (reset! parent-inv nil)
    layer)

  (apply! [_ layer ev ctx]
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
                     :commit)
        :idle)))

  (preview! [_ layer ctx]
    (let [st @state-atom]
      (when (seq (:new-events st))
        (let [canvas (:canvas layer)
              brush (get-brush)
              [events new-st] (drain-new-events st)
              new-canvas (draw-preview! canvas brush events (:stroke-length new-st))]
          (reset! state-atom new-st)
          (assoc layer :canvas new-canvas))))
    ;; 如果没有新事件，返回原图层
    (if (seq (:new-events @state-atom))
      layer
      layer))

  (commit! [_ layer ctx]
    (let [st @state-atom
          all-evs (:all-events st)
          stroke-len (:stroke-length st)]
      (when (seq all-evs)
        (let [layer-canvas (:canvas layer)
              backup-canvas (state/layer-backup (:runtime ctx))
              brush (get-brush)
              updated-canvas (commit-stroke! backup-canvas layer-canvas brush all-evs stroke-len
                                             (:canvas-id ctx) (:id layer))]
          (reset! state-atom (make-state))
          (reset! parent-inv nil)
          (assoc layer :canvas updated-canvas))))))

(defn make-brush
  "创建画笔工具实例。"
  []
  (->BrushTool (atom (make-state)) (atom nil)))
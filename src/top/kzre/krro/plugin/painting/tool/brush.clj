(ns top.kzre.krro.plugin.painting.tool.brush
  "画笔工具：完整实现 ITool 协议，利用内部 BrushState 管理事件和笔画长度。
   apply! 返回动作指令，preview! 执行插值预览，commit! 提交最终笔画。
   依赖全局状态中的 layer-buffer 作为备份。"
  (:require
    [top.kzre.krro.brush.core :as brush-core]
    [top.kzre.krro.brush.taper :as taper]
    [top.kzre.krro.canvas.core.canvas.protocol :as cp]
    [top.kzre.krro.canvas.core.floats-pool :as pool]
    [top.kzre.krro.plugin.painting.canvas.brush :as brush]
    [top.kzre.krro.plugin.painting.canvas.state :as state]
    [top.kzre.krro.plugin.painting.canvas.undo :as undo]
    [top.kzre.krro.plugin.painting.tool.protocol :as tp]
    [top.kzre.krro.plugin.undo.protocol])
  (:import
    (top.kzre.krro.canvas.core Arrays)))

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

;; ── 预览与提交的绘制函数 ──────────────────────────
(defn- get-brush []
  (or @brush/global-brush brush/default-brush))

(defn- draw-preview! [^floats dest w h brush events stroke-length]
  (when (seq events)
    (let [stroke  (brush-core/events->stroke brush events
                                             (:spacing brush) (:radius brush))
          tapered (taper/taper-stroke-start stroke (:taper-start brush)
                                            :fields [:radius :opacity]
                                            :end-dist stroke-length)]
      (brush-core/render-stroke! dest w h tapered))))

(defn- commit-stroke! [^floats dest w h brush all-events stroke-length
                       ^floats layer-backup canvas-id layer-id]
  (when (seq all-events)
    (let [buf-size (alength dest)
          temp (pool/borrow buf-size)]
      (try
        (Arrays/copy layer-backup temp)
        (let [stroke  (brush-core/events->stroke brush all-events
                                                 (:spacing brush) (:radius brush))
              tapered (taper/taper-stroke stroke (:taper-start brush) (:taper-end brush)
                                          :fields [:radius :opacity]
                                          :end-dist stroke-length)
              dirties (brush-core/render-stroke-dirties! temp w h tapered)]
          (undo/record-raster-stroke! canvas-id layer-id layer-backup temp dirties)
          (Arrays/copy temp dest))
        (finally
          (pool/return temp))))))

;; ── 工具实现 ──────────────────────────────────────
(defrecord BrushTool [state-atom]
  tp/ITool
  (begin! [_ layer _ctx]
    ;; TODO: UI 逻辑（光标、面板等）
    layer)

  (end! [_ layer _ctx]
    ;; TODO: UI 清理
    layer)

  (apply! [_ layer ev ctx]
    (let [;; 获取图层变换，提供默认值
          layer-x   (or (:x layer) 0.0)
          layer-y   (or (:y layer) 0.0)
          scale-x   (or (:scale-x layer) 1.0)
          scale-y   (or (:scale-y layer) 1.0)
          ;; 逆变换：画布坐标 -> 图层本地坐标
          local-x   (/ (- (:x ev) layer-x) (max scale-x 1e-6))
          local-y   (/ (- (:y ev) layer-y) (max scale-y 1e-6))
          local-ev  (assoc ev :x local-x :y local-y)]
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
        (let [dest (cp/data (:canvas layer))
              brush (get-brush)
              w (:width (:data ctx))
              h (:height (:data ctx))
              [events new-st] (drain-new-events st)]
          (draw-preview! dest w h brush events (:stroke-length new-st))
          (reset! state-atom new-st))))
    layer)

  (commit! [_ layer ctx]
    (let [st @state-atom
          all-evs (:all-events st)
          stroke-len (:stroke-length st)]
      (when (seq all-evs)
        (let [dest (cp/data (:canvas layer))
              brush (get-brush)
              w (:width (:data ctx))
              h (:height (:data ctx))
              backup (state/layer-buffer (:runtime ctx))]
          (commit-stroke! dest w h brush all-evs stroke-len
                          backup (:canvas-id ctx) (:id layer))
          ;; 提交后重置状态
          (reset! state-atom (make-state)))))
    layer))

(defn make-brush
  "创建画笔工具实例。"
  []
  (->BrushTool (atom (make-state))))
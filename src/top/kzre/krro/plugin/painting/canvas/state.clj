(ns top.kzre.krro.plugin.painting.canvas.state
  "运行时状态：事件、笔刷、缓冲区、累积长度。"
  (:require
   [top.kzre.krro.canvas.core.core :as canv]
   [top.kzre.krro.plugin.painting.canvas.project :as canv-proj]
   )
  (:import
   [top.kzre.krro.canvas.core Arrays]
   (top.kzre.krro.plugin.painting.canvas.project CanvasData)))


(defrecord CanvasRuntime
  [new-events        ;; atom: 本帧新事件
   all-events        ;; atom: 整个笔画事件序列（提交用）
   preview-buffer    ;; 预览缓冲区
   layer-buffer      ;; 图层原始数据备份（笔画开始时拷贝）
   last-stroke
   stroke-length;; atom: 已预览像素长度（用作 start-dist）
   ])

(defn create
  [width height]
  (let [n (* width height 4)]
    (map->CanvasRuntime
      {:new-events     (atom [])
       :all-events     (atom [])
       :preview-buffer (float-array n)
       :layer-buffer   (float-array n)
       :stroke-length  (atom 0.0)})))

(defn clone
 [ ^CanvasRuntime cr]
  (map->CanvasRuntime cr))

(defonce canvas-runtimes (atom {}))

(defn canvas-runtime [canvas-id]
  (get @canvas-runtimes canvas-id))




(defn get-all-events [rt] @(:all-events rt))
(defn get-stroke-length [rt] @(:stroke-length rt))
(defn preview-buffer [rt] (:preview-buffer rt))
(defn layer-buffer [rt] (:layer-buffer rt))

(defn begin-stroke!
  [rt]
  (reset! (:new-events rt) [])
  (reset! (:all-events rt) [])
  (reset! (:stroke-length rt) 0.0))

(defn render-canvas!
  "渲染当前画布所有图层到目标数组。"
  [canvas-id ^floats dest]
  (when-let [cd (canv-proj/canvas-data canvas-id)]
    (let [layers (:layers ^CanvasData cd)
          w (.width ^CanvasData cd)
          h (.height ^CanvasData cd)]
      (Arrays/fill dest (float 0.0))
      (canv/render-layers! layers dest w h))))

(defn ensure-runtime!
  ([canvas-id]
   (ensure-runtime! canvas-id 800 600))
  ([canvas-id w h]
   (or (canvas-runtime canvas-id)
       (let [_cd (canv-proj/ensure-canvas-data! canvas-id w h)
             rt (create w h)
             preview (:preview-buffer rt)]
         (render-canvas! canvas-id preview)
         (swap! canvas-runtimes assoc canvas-id rt)
         rt))))

(defn push-event! [rt event]
  (let [last-p (last @(:all-events rt))]   ;; 上一个事件
    (swap! (:new-events rt) conj event)
    (swap! (:all-events rt) conj event)
    (when last-p
      (let [dx   (- (:x event) (:x last-p))
            dy   (- (:y event) (:y last-p))
            dist (Math/sqrt (+ (* dx dx) (* dy dy)))]
        (swap! (:stroke-length rt) + dist)))))

;; 事件窗口控制，带来更流程的笔触预览
(def ^:private min-keep-distance 10.0)   ;; 保留事件覆盖的最小像素距离
(def ^:private max-keep-count 50)        ;; 保留事件的最大数量
(def ^:private min-keep-count 5)         ;; 至少保留的事件数量（除非事件总数不足）

(defn drain-new-events
  "获取本帧新事件，保留末尾一段事件以确保帧间插值平滑。
   保留规则：
   - 至少保留 min-keep-count 个事件（如果可用）
   - 同时尽量覆盖至少 min-keep-distance 像素距离
   - 保留数量不超过 max-keep-count
   返回完整的新事件向量（包含保留的事件）。"
  [rt]
  (let [evs (vec @(:new-events rt))
        cnt (count evs)]
    (if (<= cnt min-keep-count)          ;; 总数不足，全部保留
      evs
      (let [keep-start
            (loop [i (dec cnt)
                   dist 0.0
                   kept-num 1            ;; 已经将最后一个事件计入
                   last-x (:x (nth evs i))
                   last-y (:y (nth evs i))]
              (let [prev-i (dec i)
                    prev-x (:x (nth evs prev-i))
                    prev-y (:y (nth evs prev-i))
                    dx (- last-x prev-x)
                    dy (- last-y prev-y)
                    d (Math/sqrt (+ (* dx dx) (* dy dy)))
                    new-dist (+ dist d)
                    new-kept-num (inc kept-num)]
                (if (or (zero? prev-i)                          ;; 已到开头
                        (and (>= new-kept-num min-keep-count)
                             (>= new-dist min-keep-distance))   ;; 满足最小数量和距离
                        (>= new-kept-num max-keep-count))       ;; 达到最大数量
                  i   ;; 返回当前 i 作为保留起始索引
                  (recur prev-i new-dist new-kept-num prev-x prev-y))))
            keep-evs (subvec evs keep-start)]
        (reset! (:new-events rt) keep-evs)
        evs))))

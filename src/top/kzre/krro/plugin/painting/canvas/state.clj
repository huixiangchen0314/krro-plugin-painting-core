(ns top.kzre.krro.plugin.painting.canvas.state
  "运行时状态：事件、笔刷、缓冲区、累积长度。"
  (:require
    [top.kzre.krro.canvas.core.core :as canv]
    [top.kzre.krro.canvas.core.layer.core :as lc]
    [top.kzre.krro.core.frame :as frame]
    [top.kzre.krro.plugin.painting.project.canvas :as pc]
    [top.kzre.krro.plugin.painting.spec :as spec])
  (:import
   (top.kzre.krro.canvas.core Arrays)
   (top.kzre.krro.plugin.painting.project.canvas CanvasData)))

(defn frames-with-canvas-id
  "返回所有显示指定画布的 Frame。"
  [canvas-id]
  (frame/frames-with-param spec/canvas-id-key canvas-id))

(defrecord CanvasRuntime
  [new-events         ;; 本帧新事件
   all-events         ;; 整个笔画事件序列（提交用）
   ^floats preview-buffer     ;; 预览缓冲区
   ^floats layer-buffer       ;; 光栅图层原始数据备份（笔画开始时拷贝）
   selected-layer-id  ;; 当前选中的图层id
   stroke-length      ;; 已预览像素长度（用作 start-dist）
   ])

(defn default-state
  [buffer-size]
  {:new-events     []
   :all-events     []
   :preview-buffer (float-array buffer-size)
   :layer-buffer   (float-array buffer-size)
   :selected-layer-id nil
   :stroke-length  0.0})

(defn make-state
  [width height]
  (let [n (* width height 4)]
    (map->CanvasRuntime
      (default-state n))))

(defonce canvas-runtimes (atom {}))

(defn canvas-runtime [canvas-id]
  (get @canvas-runtimes canvas-id))

(defn selected-layer-id [canvas-id]
  (when-let [rt (canvas-runtime canvas-id)]
    (:selected-layer-id rt)))

(defn selected-layer! [canvas-id]
  (when-let [rt (pc/canvas-data! canvas-id)]
    (when-let [lid (selected-layer-id canvas-id)]
      (let [ls (:layers rt)]
        (lc/find-layer lid ls)))))

(defn selected-layer-type [canvas-id]
  (when-let [lid (selected-layer-id canvas-id)]
    (let [layers (pc/layers-by-id canvas-id)]
      (when-let [l (lc/find-layer lid layers)]
        (:type l)))))

(defn get-all-events [^CanvasRuntime rt] (:all-events rt))
(defn get-stroke-length [^CanvasRuntime rt] (:stroke-length rt))
(defn preview-buffer [^CanvasRuntime rt] (:preview-buffer rt))
(defn layer-buffer [^CanvasRuntime rt] (:layer-buffer rt))

(defn get-all-events-by-id [canvas-id]
  (when-let [^CanvasRuntime rt (canvas-runtime canvas-id)]
    (:all-events rt)))

(defn get-stroke-length-by-id [canvas-id]
  (when-let [^CanvasRuntime rt (canvas-runtime canvas-id)]
    (:stroke-length rt)))

(defn preview-buffer-by-id [canvas-id]
  (when-let [^CanvasRuntime rt (canvas-runtime canvas-id)]
    (:preview-buffer rt)))

(defn layer-buffer-by-id [canvas-id]
  (when-let [^CanvasRuntime rt (canvas-runtime canvas-id)]
    (:layer-buffer rt)))

(defn begin-stroke [rt]
  (assoc rt
    :new-events []
    :all-events []
    :stroke-length 0.0))

(defn begin-stroke!
  "副作用函数：开始新笔画，清空事件和长度。"
  [canvas-id]
  (swap! canvas-runtimes
         (fn [rts]
           (if-let [rt (get rts canvas-id)]
             (assoc rts canvas-id (begin-stroke rt))
             rts))))

(declare ensure-runtime!)

(defn render-canvas!
  "渲染当前画布所有图层到目标数组。"
  ([canvas-id]
   (let [rt (ensure-runtime! canvas-id)
         preview (:preview-buffer rt)]
     (render-canvas! canvas-id preview)))
  ([canvas-id ^floats dest]
   (when-let [cd (pc/canvas-data! canvas-id)]
     (let [layers (:layers ^CanvasData cd)
           w (:width ^CanvasData cd)
           h (:height ^CanvasData cd)]
       (Arrays/fill dest (float 0.0))
       (canv/render-layers! layers dest w h)))))

(defn ensure-runtime!
  ([canvas-id]
   (ensure-runtime! canvas-id 800 600))
  ([canvas-id w h]
   (or (canvas-runtime canvas-id)
       (let [_cd (pc/ensure-canvas-data! canvas-id w h)
             rt (make-state w h)
             preview (:preview-buffer rt)]
         (render-canvas! canvas-id preview)
         (swap! canvas-runtimes assoc canvas-id rt)
         rt))))

(defn push-event [^CanvasRuntime rt event]
  (let [old-all    (:all-events rt)
        last-event (peek old-all)
        new-all    (conj old-all event)
        new-new    (conj (:new-events rt) event)
        dist       (if last-event
                     (Math/sqrt (+ (Math/pow (- (:x event) (:x last-event)) 2)
                                   (Math/pow (- (:y event) (:y last-event)) 2)))
                     0.0)]
    (-> rt
        (assoc :new-events new-new)
        (assoc :all-events new-all)
        (update :stroke-length + dist))))

(defn push-event!
  "副作用函数：向指定画布的事件流中加入新事件，原子更新。"
  [canvas-id event]
  (swap! canvas-runtimes
         (fn [rts]
           (if-let [rt (get rts canvas-id)]
             (assoc rts canvas-id (push-event rt event))
             rts))))

;; 事件窗口控制，带来更流程的笔触预览
(def ^:private min-keep-distance 10.0)   ;; 保留事件覆盖的最小像素距离
(def ^:private max-keep-count 50)        ;; 保留事件的最大数量
(def ^:private min-keep-count 5)         ;; 至少保留的事件数量（除非事件总数不足）

(defn- compute-keep-start [evs]
  (let [cnt (count evs)]
    (if (<= cnt min-keep-count)
      0   ; 全部保留
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
              i   ;; 从此处开始保留
              (recur prev-i new-dist new-kept-num prev-x prev-y))))))))

(defn drain-new-events [rt]
  (let [evs (:new-events rt)
        cnt (count evs)]
    (if (<= cnt min-keep-count)
      [evs rt]   ;; 事件全部保留，运行时不变
      (let [keep-start (compute-keep-start evs)
            keep-evs   (subvec evs keep-start)
            new-rt     (assoc rt :new-events keep-evs)]
        [evs new-rt]))))

(defn drain-new-events!
  "副作用函数：取出当前帧的新事件，同时更新运行时。返回事件向量。"
  [canvas-id]
  (let [result (atom nil)]
    (swap! canvas-runtimes
           (fn [rts]
             (if-let [rt (get rts canvas-id)]
               (let [[events new-rt] (drain-new-events rt)]
                 (reset! result events)
                 (assoc rts canvas-id new-rt))
               rts)))
    @result))

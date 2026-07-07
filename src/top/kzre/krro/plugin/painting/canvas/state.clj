(ns top.kzre.krro.plugin.painting.canvas.state
  "画布运行时状态，视图本地。使用事件序列原子支持笔画累积。"
  (:import [top.kzre.krro.plugin.painting.canvas.project CanvasData]))

(defrecord CanvasRuntime
  [events           ;; atom: 当前笔划的完整事件向量
   current-brush    ;; atom: 当前笔刷
   preview-buffer   ;; 临时预览缓冲区
   stroke-buffer    ;; 笔画累积缓冲区（从持久化拷贝起点）
   canvas-data])    ;; 持久化数据引用

(defn create [^CanvasData canvas-data]
  (let [w (.width canvas-data)
        h (.height canvas-data)
        n (* w h 4)]
    (map->CanvasRuntime
      {:events        (atom [])
       :current-brush (atom nil)
       :preview-buffer (float-array n)
       :stroke-buffer  (float-array n)
       :canvas-data    canvas-data})))

(defn push-event! [^CanvasRuntime rt event]
  (swap! (:events rt) conj event))

(defn get-events [^CanvasRuntime rt]
  @(:events rt))

(defn begin-stroke! [^CanvasRuntime rt]
  (reset! (:events rt) [])
  ;; 拷贝持久化数据到笔画累积缓冲区，作为起点
  (let [persistent (.data ^CanvasData (:canvas-data rt))
        stroke    (:stroke-buffer rt)]
    (System/arraycopy persistent 0 stroke 0 (alength persistent))))

(defn current-brush [^CanvasRuntime rt]
  @(:current-brush rt))

(defn set-current-brush! [^CanvasRuntime rt brush]
  (reset! (:current-brush rt) brush))

(defn persistent-pixels [^CanvasRuntime rt]
  (.data ^CanvasData (:canvas-data rt)))

(defn canvas-size [^CanvasRuntime rt]
  (let [cd ^CanvasData (:canvas-data rt)]
    [(.width cd) (.height cd)]))

(defn preview-buffer [^CanvasRuntime rt]
  (:preview-buffer rt))

(defn stroke-buffer [^CanvasRuntime rt]
  (:stroke-buffer rt))
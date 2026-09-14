(ns top.kzre.krro.plugin.painting.core.render
  "渲染线程"
  (:require
   [taoensso.timbre :as log]
   [top.kzre.krro.canvas.core.core :as canv]
   [top.kzre.krro.core.util.promise :as promise]
   [top.kzre.krro.plugin.painting.core.layer.clone :as clone]
   [top.kzre.krro.plugin.painting.core.layer.dispose :as dispose]
   [top.kzre.krro.plugin.painting.core.model.tiled-image]
   [top.kzre.krro.plugin.painting.core.viewport :as vp]
   [top.kzre.krro.plugin.painting.core.project.canvas :as pc])
  (:import
   (java.util Set)
   (java.util.concurrent ExecutorService Executors ThreadFactory TimeUnit)
   (top.kzre.krro.canvas.core.layer LayerUtils)
   (top.kzre.krro.core.util CoalescedTask ExecutorServiceAsyncAdapter LatestTaskRunner Mergable)
   (top.kzre.krro.plugin.painting.core.model.tiled_image TiledImage)
   (top.kzre.krro.util.math KMath)
   (top.kzre.krro.util.tile CanvasUtils)))


;; ═══════════════════════════════════════════════
;; 脏区计算（不变）
;; ═══════════════════════════════════════════════

(defn dirty-region
  [region transform viewport-w viewport-h tile-size]
  (cond
    (nil? region) nil
    (not (seq region)) #{}
    (or (set? region) (instance? Set region))
    (if transform
      (let [screen-tiles (LayerUtils/transformTiles region tile-size transform)]
        (if (and viewport-w viewport-h)
          (set (CanvasUtils/clipTiles screen-tiles tile-size viewport-w viewport-h))
          screen-tiles))
      (if (and viewport-w viewport-h)
        (set (CanvasUtils/clipTiles region tile-size viewport-w viewport-h))
        region))
    (map? region)
    (let [{:keys [min-x min-y max-x max-y]} region
          corners        [[min-x min-y] [max-x min-y]
                          [max-x max-y] [min-x max-y]]
          screen-corners (if transform
                           (map (fn [[x y]]
                                  (KMath/mat2dTransformPoint transform (float x) (float y)))
                                corners)
                           (map (fn [[x y]] [(double x) (double y)]) corners))
          xs             (map first screen-corners)
          ys             (map second screen-corners)
          screen-min-x   (apply min xs)
          screen-max-x   (apply max xs)
          screen-min-y   (apply min ys)
          screen-max-y   (apply max ys)
          clipped-min-x  (max screen-min-x 0.0)
          clipped-max-x  (min screen-max-x (double viewport-w))
          clipped-min-y  (max screen-min-y 0.0)
          clipped-max-y  (min screen-max-y (double viewport-h))]
      (if (and (< clipped-min-x clipped-max-x)
               (< clipped-min-y clipped-max-y))
        (set (LayerUtils/aabbTiles tile-size
                                   clipped-min-x clipped-min-y
                                   clipped-max-x clipped-max-y))
        #{}))
    :else
    (throw (IllegalArgumentException.
             (str "region must be Set or Map, got " (type region))))))

;; ═══════════════════════════════════════════════
;; 渲染参数
;; ═══════════════════════════════════════════════

(defrecord RenderParams [^TiledImage image
                         viewport-dirty-tiles
                         layers
                         viewport
                         viewport-w
                         viewport-h]
  Mergable
  (merge [this new-params]
    (log/debug "merge render task")
    (when-let [old-cloned (:layers this)]
      (run! dispose/dispose-layer old-cloned))
    (let [old-image    (:image this)
          new-image    (:image new-params)
          old-vp-w     (:viewport-w this)
          old-vp-h     (:viewport-h this)
          new-vp-w     (:viewport-w new-params)
          new-vp-h     (:viewport-h new-params)
          old-dirty    (:viewport-dirty-tiles this)
          new-dirty    (:viewport-dirty-tiles new-params)
          merged-dirty (and (seq old-dirty) (seq new-dirty)
                            (into old-dirty new-dirty))
          force-full   (or (not= old-vp-w new-vp-w)
                           (not= old-vp-h new-vp-h)
                           (not= (:width  old-image) (:width  new-image))
                           (not= (:height old-image) (:height new-image)))]
      (assoc new-params :viewport-dirty-tiles
                        (if force-full nil merged-dirty)))))

;; ═══════════════════════════════════════════════
;; 渲染任务
;; ═══════════════════════════════════════════════

(defonce ^:private render-task
         (reify CoalescedTask
           (run [_ params]
             (let [{:keys [image viewport viewport-dirty-tiles
                           viewport-w viewport-h layers]} params
                   {:keys [canvas width height]} image]
               (when (or (nil? viewport-dirty-tiles)
                         (not-empty viewport-dirty-tiles))
                 (try
                   (let [result
                         (promise/await
                           (canv/render-layers!
                             layers
                             :tile-size pc/global-tile-size
                             :view-width       viewport-w
                             :view-height      viewport-h
                             :view-matrix      (when viewport (vp/viewport->mat2d viewport))
                             :view-dirty-tiles viewport-dirty-tiles
                             :image-width      width
                             :image-height     height))
                         diff-canvas (:canvas result)
                         dirty-tiles (:dirty-tiles result)]
                     (.deleteTiles canvas ^Set dirty-tiles)
                     (.mergeCanvas canvas diff-canvas)
                     (.clear diff-canvas))
                   (catch Throwable t
                     (log/error t "composite render failed"))
                   (finally
                     (try
                       (run! dispose/dispose-layer layers)
                       (catch Exception _ nil)))))))))

;; ═══════════════════════════════════════════════
;; 全局执行器——标准单线程池 + 适配器
;; ═══════════════════════════════════════════════

(defonce ^:private ^ExecutorService composite-pool
         (Executors/newSingleThreadExecutor
           (reify ThreadFactory
             (newThread [_ r]
               (doto (Thread. r "krro-composite-worker")
                 (.setDaemon true))))))

(defonce ^LatestTaskRunner executor
         (LatestTaskRunner.
           render-task
           (ExecutorServiceAsyncAdapter. composite-pool)))


;; ═══════════════════════════════════════════════
;; 提交接口
;; ═══════════════════════════════════════════════

(defn render-layers-viewport!
  [task-id ^TiledImage image layers dirties dirty-transform
   viewport viewport-w viewport-h]
  (let [tile-size          (.getTileSize (:canvas image))
        combined-transform (when (and dirties dirty-transform)
                             (KMath/mat2dMul (vp/viewport->mat2d viewport)
                                             dirty-transform))
        viewport-dirty-tiles (dirty-region dirties combined-transform
                                           viewport-w viewport-h tile-size)
        params (->RenderParams image
                               viewport-dirty-tiles
                               (mapv clone/clone-layer layers)
                               viewport viewport-w viewport-h)]
    (log/debug (format "composite: dirties=%s, combined-transform=%s, viewport-dirty-tiles=%s"
                       dirties combined-transform viewport-dirty-tiles))
    (promise/from-completable-future
      (.submit executor task-id params))))

;; ═══════════════════════════════════════════════
;; 生命周期
;; ═══════════════════════════════════════════════

(defn shutdown!
  "关闭全局合成服务——drain 队列并停止线程池。幂等。"
  []
  (.close executor)
  (.shutdown composite-pool)
  (try
    (when-not (.awaitTermination composite-pool 5 TimeUnit/SECONDS)
      (.shutdownNow composite-pool))
    (catch InterruptedException e
      (.interrupt (Thread/currentThread))
      (.shutdownNow composite-pool))))
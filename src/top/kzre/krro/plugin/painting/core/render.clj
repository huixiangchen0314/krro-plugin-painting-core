(ns top.kzre.krro.plugin.painting.core.render
  (:require
   [taoensso.timbre :as log]
   [top.kzre.krro.canvas.core.core :as canv]
   [top.kzre.krro.plugin.painting.core.layer.clone :as clone]
   [top.kzre.krro.plugin.painting.core.layer.dispose :as dispose]
   [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
   [top.kzre.krro.plugin.painting.core.viewport :as vp])
  (:import
   [java.util Set]
   (top.kzre.krro.canvas.core.layer LayerUtils)
   (top.kzre.krro.core.util LastestTaskExecutor)
   (top.kzre.krro.core.util LastestTaskExecutor$TaskParams)
   (top.kzre.krro.core.util LastestTaskExecutor$TaskDefinition)
   (top.kzre.krro.util.math KMath)
   (top.kzre.krro.util.tile CanvasUtils TiledCanvas)))

(defn render-canvas
  "渲染图层到目标画布。canvas-w 和 canvas-h 为视口尺寸（渲染区域大小）。"
  [layers canvas-w canvas-h dirty-tiles ^TiledCanvas dest
   & {:keys [viewport]}]
  (cond
    (nil? dirty-tiles)
    (do
      (.clear dest)
      (canv/render-layers! layers dest canvas-w canvas-h
                           :viewport viewport))
    (empty? dirty-tiles) nil

    :else
    (let [tile-size (.getTileSize dest)]
      (assert (= pc/global-tile-size tile-size))
      (canv/render-layers! layers dest canvas-w canvas-h
                           :dirty-tiles (CanvasUtils/clipTiles dirty-tiles tile-size canvas-w canvas-h)
                           :viewport viewport
                           :tile-size tile-size
                           ))))

;; ── 渲染任务参数（包含克隆图层） ──────────────
(defrecord RenderParams [key canvas canvas-data dirty-tiles
                         layers viewport canvas-w canvas-h upload-fn ]
  LastestTaskExecutor$TaskParams
  (key [_] key))

;; ── 任务定义（合并 + 执行） ──────────────────────
(def render-task-def
  (reify LastestTaskExecutor$TaskDefinition
    (mergeTask [_ current new]
      ;; 释放旧任务的克隆图层
      (when-let [old-cloned (:layers current)]
        (doseq [l old-cloned]
          (dispose/dispose-layer l)))
      ;; 合并脏区域，视口尺寸变化则全量渲染
      (let [old-data (:canvas-data current)
            new-data (:canvas-data new)
            old-vp-w (:canvas-w current)
            old-vp-h (:canvas-h current)
            new-vp-w (:canvas-w new)
            new-vp-h (:canvas-h new)
            old-dirty (:dirty-tiles current)
            new-dirty (:dirty-tiles new)
            merged-dirty (and (seq old-dirty) (seq new-dirty) (into old-dirty new-dirty))
            ;; 如果视口尺寸或图像尺寸变化，强制全量
            force-full (or (not= old-vp-w new-vp-w)
                           (not= old-vp-h new-vp-h)
                           (not= (:width old-data) (:width new-data))
                           (not= (:height old-data) (:height new-data)))]
        (assoc new :dirty-tiles (if force-full nil merged-dirty))))

    (runTask [_ params]
      (let [{:keys [canvas canvas-data  viewport
                    dirty-tiles
                    canvas-w canvas-h
                    layers upload-fn ]} params
            {:keys [width height]} canvas-data]   ; 保留用于其他用途，但渲染边界使用视口尺寸
        (try
          (log/debug (format "runTask: dirty-tiles=%s" dirty-tiles))
          (render-canvas layers canvas-w canvas-h dirty-tiles canvas
                         :viewport (when viewport (vp/viewport->mat2d viewport)))
          (when upload-fn
            (upload-fn canvas canvas-data viewport))
          (catch Exception e
            (log/error e "Render task failed"))
          (finally
            (doseq [l layers]
              (dispose/dispose-layer l))))))))

;; ── 全局执行器 ──────────────────────────────────
(defonce ^LastestTaskExecutor executor
         (LastestTaskExecutor. render-task-def))

(defn dirty-region
  "将 region（Set<Long> 或 AABB Map）转换为屏幕空间的脏瓦片集合。
   - region: 可以是 Set<Long>（瓦片编码）或 Map {:min-x :min-y :max-x :max-y}。
   - transform: 可选矩阵（float[] 长度为 6），用于将图层坐标转换为屏幕坐标。
   - viewport-w, viewport-h: 视口尺寸（像素），用于裁剪。
   - tile-size: 瓦片大小（像素）。
   返回 Set<Long>（屏幕空间瓦片编码），可能为空。"
  [region transform viewport-w viewport-h tile-size]
  (cond
    (nil? region) nil
    (not (seq region)) #{}
    (or (set? region) (instance? Set region))
    (if transform
      (let [screen-tiles (LayerUtils/transformTiles region tile-size transform)]
        (if (and viewport-w viewport-h)
          (set
            (CanvasUtils/clipTiles screen-tiles tile-size viewport-w viewport-h))
          screen-tiles))
      (if (and viewport-w viewport-h)
        (set
          (CanvasUtils/clipTiles region tile-size viewport-w viewport-h))
        region))
    (map? region)
    (let [{:keys [min-x min-y max-x max-y]} region
          corners [[min-x min-y] [max-x min-y] [max-x max-y] [min-x max-y]]
          screen-corners (if transform
                           (map (fn [[x y]] (KMath/mat2dTransformPoint transform (float x) (float y))) corners)
                           (map (fn [[x y]] [(double x) (double y)]) corners))
          xs (map first screen-corners)
          ys (map second screen-corners)
          screen-min-x (apply min xs)
          screen-max-x (apply max xs)
          screen-min-y (apply min ys)
          screen-max-y (apply max ys)
          clipped-min-x (max screen-min-x 0.0)
          clipped-max-x (min screen-max-x (double viewport-w))
          clipped-min-y (max screen-min-y 0.0)
          clipped-max-y (min screen-max-y (double viewport-h))]
      (if (and (< clipped-min-x clipped-max-x) (< clipped-min-y clipped-max-y))
        (set
          (LayerUtils/aabbTiles tile-size
                                clipped-min-x clipped-min-y
                                clipped-max-x clipped-max-y))
        #{}))
    :else (throw (IllegalArgumentException. (str "region must be Set or Map, got " (type region))))))


(defn request-render-full!
  [render-task-id canvas canvas-data upload-fn & {:keys [dirties dirty-transform]}]
  (let [tile-size (.getTileSize canvas)
        viewport-w (:width canvas-data)
        viewport-h (:height canvas-data)
        screen-dirty (dirty-region dirties dirty-transform viewport-w viewport-h tile-size)
        params (->RenderParams render-task-id canvas canvas-data
                               screen-dirty
                               (mapv clone/clone-layer (:layers canvas-data))
                               vp/default-viewport
                               viewport-w viewport-h
                               upload-fn)]
    (.submit executor render-task-id params)))

;; ── 渲染请求入口（提交时克隆图层） ──────────────
(defn request-render-viewport!
  "提交渲染任务，视口尺寸单独传入，用于裁剪渲染区域。"
  [render-task-id canvas canvas-data dirties dirty-transform viewport viewport-w viewport-h upload-fn]
  (let [tile-size (.getTileSize canvas)
        combined-transform (when (and dirties dirty-transform)
                             (KMath/mat2dMul (vp/viewport->mat2d viewport) dirty-transform))
        screen-dirty (dirty-region dirties combined-transform viewport-w viewport-h tile-size)
        params (->RenderParams render-task-id canvas canvas-data
                               screen-dirty
                               (mapv clone/clone-layer (:layers canvas-data))
                               viewport viewport-w viewport-h
                               upload-fn)]
    (log/debug (format "render-viewport: dirties=%s, combined-transform=%s, screen-dirty=%s"
                       dirties combined-transform screen-dirty))
    (.submit executor render-task-id params)))

;; ── 关闭执行器 ──────────────────────────────────
(defn shutdown! []
  (.shutdown executor))
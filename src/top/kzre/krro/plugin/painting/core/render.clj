(ns top.kzre.krro.plugin.painting.core.render
  "渲染线程"
  (:require
   [taoensso.timbre :as log]
   [top.kzre.krro.canvas.core.core :as canv]
   [top.kzre.krro.plugin.painting.core.layer.clone :as clone]
   [top.kzre.krro.plugin.painting.core.layer.dispose :as dispose]
   [top.kzre.krro.plugin.painting.core.model.tiled-image]
   [top.kzre.krro.plugin.painting.core.viewport :as vp]
   [top.kzre.krro.core.util.promise :as promise])
  (:import
    (java.util Set)
    (top.kzre.krro.canvas.core.layer LayerUtils)
    (top.kzre.krro.core.util LastestTaskExecutor)
    (top.kzre.krro.core.util LastestTaskExecutor$TaskParams)
    (top.kzre.krro.core.util LastestTaskExecutor$TaskDefinition)
    (top.kzre.krro.plugin.painting.core.model.tiled_image TiledImage)
    (top.kzre.krro.util.math KMath)
    (top.kzre.krro.util.tile CanvasUtils)))

;; ── 渲染任务参数（包含克隆图层） ──────────────
(defrecord RenderParams [key
                         ^TiledImage image
                         viewport-dirty-tiles
                         layers viewport viewport-w viewport-h done!]
  LastestTaskExecutor$TaskParams
  (key [_] key))

;; ── 任务定义（合并 + 执行） ──────────────────────
(def render-task-def
  (reify LastestTaskExecutor$TaskDefinition
    (mergeTask [_ current new]
      (log/debug "merge render task")
      ;; 释放旧任务的克隆图层
      (when-let [old-cloned (:layers current)]
        (doseq [l old-cloned]
          (dispose/dispose-layer l)))
      ;; 合并脏区域，视口尺寸变化则全量渲染
      (let [old-image (:image current)
            new-image (:image new)
            old-vp-w (:viewport-w current)
            old-vp-h (:viewport-h current)
            new-vp-w (:viewport-w new)
            new-vp-h (:viewport-h new)
            old-dirty (::viewport-dirty-tiles current)
            new-dirty (::viewport-dirty-tiles new)
            merged-dirty (and (seq old-dirty) (seq new-dirty) (into old-dirty new-dirty))
            ;; 如果视口尺寸或图像尺寸变化，强制全量
            force-full (or (not= old-vp-w new-vp-w)
                           (not= old-vp-h new-vp-h)
                           (not= (:width old-image) (:width new-image))
                           (not= (:height old-image) (:height new-image)))]
        (assoc new ::viewport-dirty-tiles (if force-full nil merged-dirty))))

    (runTask [_ params]
      (let [{:keys [image viewport
                    viewport-dirty-tiles
                    viewport-w viewport-h
                    layers done!]} params
            {:keys [canvas width height]} image]   ; 保留用于其他用途，但渲染边界使用视口尺寸
        (when
          (or
            (nil? viewport-dirty-tiles) ;; 全量渲染
            (not-empty viewport-dirty-tiles)   ;; 部分更新
            )
          (->
            (canv/render-layers!
              layers canvas
              :view-width viewport-w
              :view-height viewport-h
              :view-matrix (when viewport (vp/viewport->mat2d viewport))
              :view-dirty-tiles viewport-dirty-tiles
              :image-width width
              :image-height height)
            (promise/fmap (fn [c] (done!) c))
            (promise/handle
              (fn [v e]
                (try
                  (run! dispose/dispose-layer layers)
                  (catch Exception _ nil))
                (when e (log/error "render failed: " e))
                v))))))))

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


;; ── 渲染请求入口（提交时克隆图层） ──────────────
(defn request-render-viewport!
  "提交渲染任务，视口尺寸单独传入，用于裁剪渲染区域。"
  [render-task-id
   ^TiledImage image
   layers dirties dirty-transform
   viewport viewport-w viewport-h
   done!]
  (let [tile-size (.getTileSize (:canvas image))
        combined-transform (when (and dirties dirty-transform)
                             (KMath/mat2dMul (vp/viewport->mat2d viewport) dirty-transform))
        viewport-dirty-tiles (dirty-region dirties combined-transform viewport-w viewport-h tile-size)
        params (->RenderParams render-task-id  image
                               viewport-dirty-tiles
                               (mapv clone/clone-layer layers)
                               viewport viewport-w viewport-h
                               done!)]
    (log/debug (format "render-viewport: dirties=%s, combined-transform=%s, viewport-dirty-tiles=%s"
                       dirties combined-transform viewport-dirty-tiles))
    (.submit executor render-task-id params)))

;; ── 关闭执行器 ──────────────────────────────────
(defn shutdown! []
  (.shutdown executor))
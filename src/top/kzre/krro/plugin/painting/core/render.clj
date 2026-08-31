(ns top.kzre.krro.plugin.painting.core.render
  (:require
    [taoensso.timbre :as log]
    [top.kzre.krro.canvas.core.core :as canv]
    [top.kzre.krro.plugin.painting.core.layer.clone :as clone]
    [top.kzre.krro.plugin.painting.core.layer.dispose :as dispose]
    [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
    [top.kzre.krro.plugin.painting.core.viewport :as vp])
  (:import
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
      (canv/render-layers! layers dest canvas-w canvas-h
                           :dirty-tiles (CanvasUtils/clipTiles dirty-tiles tile-size canvas-w canvas-h)
                           :viewport viewport
                           :tile-size pc/global-tile-size
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
            merged-dirty (when-not (or (nil? old-dirty) (nil? new-dirty))
                           (into old-dirty new-dirty))
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

(defn request-render-full!
  [render-task-id canvas canvas-data upload-fn & {:keys [dirty-tiles dirty-transform]}]
  (let [layers (:layers canvas-data)
        tile-size (.getTileSize canvas)
        cloned-layers (mapv clone/clone-layer layers)
        params (->RenderParams render-task-id canvas canvas-data
                               (when (and dirty-tiles dirty-transform)
                                 (set (LayerUtils/transformTiles
                                        dirty-tiles
                                        tile-size
                                        dirty-transform)))
                               cloned-layers
                               vp/default-viewport (:width canvas-data) (:height canvas-data)
                               upload-fn )]
    (.submit executor render-task-id params)))

;; ── 渲染请求入口（提交时克隆图层） ──────────────
(defn request-render-viewport!
  "提交渲染任务，视口尺寸单独传入，用于裁剪渲染区域。"
  [render-task-id canvas canvas-data dirty-tiles dirty-transform viewport viewport-w viewport-h upload-fn]
  (let [layers (:layers canvas-data)
        tile-size (.getTileSize canvas)
        cloned-layers (mapv clone/clone-layer layers)
        params (->RenderParams render-task-id canvas canvas-data
                               (when (and dirty-tiles dirty-transform)
                                 (set (LayerUtils/transformTiles
                                        dirty-tiles
                                        tile-size
                                        (KMath/mat2dMul (vp/viewport->mat2d viewport)
                                                        dirty-transform))))
                               cloned-layers
                               viewport viewport-w viewport-h
                               upload-fn )]
    (.submit executor render-task-id params)))

;; ── 关闭执行器 ──────────────────────────────────
(defn shutdown! []
  (.shutdown executor))
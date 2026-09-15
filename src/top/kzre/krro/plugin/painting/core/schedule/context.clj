(ns top.kzre.krro.plugin.painting.core.schedule.context
  "上下文以及预处理"
  (:require
   [top.kzre.krro.canvas.core.layer.util :as layer-util]
   [top.kzre.krro.core.util.computing-graph :as cg]
   [top.kzre.krro.plugin.painting.core.schedule.util :as util]
   [top.kzre.krro.plugin.painting.core.viewport :as vp]
   [top.kzre.krro.core.util.promise :as promise])
  (:import
   (top.kzre.krro.canvas.core.layer LayerUtils)
   (top.kzre.krro.util.math KMath)))

(defonce ^:private context-key* ::context)

(defn context-key [] context-key*)

(defrecord ContextNode [ctx]
  cg/INode
  (node-id [_] (context-key))
  (dependencies [_] #{})
  (compute [_ _] (promise/resolved ctx)))

(defn make-context-node [ctx]
  (->ContextNode ctx))

(defn diff-info [old-ctx new-ctx]
  (if old-ctx
    {:same-viewport? (= (:viewport old-ctx) (:viewport new-ctx))}
    {:same-viewport? false}))

(defn assoc-view-matrix [ctx old-ctx same-viewport?]
  (if same-viewport?
    (assoc ctx :view-matrix (:view-matrix old-ctx))
    (assoc ctx :view-matrix (vp/viewport->mat2d (:viewport ctx)))))

(defn assoc-view-dirty-tiles [ctx _old-ctx]
  (let [{:keys [tile-size
                view-matrix viewport-w viewport-h
                dirty-tiles dirty-transform]} ctx
        transform-to-view
        (when (and view-matrix dirty-tiles dirty-transform)
          (KMath/mat2dMul view-matrix dirty-transform))
        view-dirty-tiles (util/dirty-region dirty-tiles transform-to-view
                                            viewport-w viewport-h tile-size)

        ;; 裁剪脏瓦片到视口
        view-clipped-dirty-tiles
        (LayerUtils/clipTiles view-dirty-tiles tile-size viewport-w viewport-h)
        ]
    (assoc ctx :view-dirty-tiles view-clipped-dirty-tiles)))

(defn assoc-image-dirty-tiles [ctx _old-ctx ]
  (let [{:keys [view-dirty-tiles view-matrix tile-size
                image-width image-height ]} ctx

        ;; 裁剪到图像范围（若提供）
        image-clipped-dirty-tiles
        (if (and image-width image-height view-matrix)
          (let [pmin (layer-util/transform-point view-matrix 0 0)
                pmax (layer-util/transform-point view-matrix image-width image-height)]
            (LayerUtils/clipTilesAABB view-dirty-tiles tile-size
                                      (:x pmin) (:y pmin)
                                      (:x pmax) (:y pmax)))
          view-dirty-tiles)]
    (assoc ctx :image-dirty-tiles image-clipped-dirty-tiles)))

(defn assoc-image-size [ctx _old-ctx]
  (let [canvas-data (:canvas-data ctx)]
    (assoc ctx :image-width (:width canvas-data)
               :image-height (:height canvas-data))))

(defn diff
  [old-ctx new-ctx {:keys [same-viewport?]}]
  (-> new-ctx
      (assoc-view-matrix old-ctx same-viewport?)
      (assoc-view-dirty-tiles old-ctx)
      (assoc-image-dirty-tiles old-ctx)))


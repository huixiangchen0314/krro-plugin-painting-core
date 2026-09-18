(ns top.kzre.krro.plugin.painting.core.schedule.vector-layer
  "矢量图层渲染"
  (:require
    [top.kzre.krro.canvas.vector.core :as vector-layer]
    [top.kzre.krro.core.util.computing-graph :as cg]
    [top.kzre.krro.core.util.promise :as promise]
    [top.kzre.krro.plugin.painting.core.schedule.graph :as graph]
    [top.kzre.krro.plugin.painting.core.schedule.layer-impl :as layer-impl])
  (:import
    (top.kzre.krro.util.tile TiledCanvas)))

(defrecord VectorLayerNode [vector-layer]
  cg/INode
  (node-id [_] (:id vector-layer))
  (dependencies [_] [:context])
  (compute [this [{:keys [tile-size viewport-w viewport-h image-dirty-tiles]}]]
    (let [canvas (TiledCanvas. tile-size)]
      (try
        (vector-layer/render-to-canvas! vector-layer canvas
                                        {:view-width  viewport-w
                                         :view-height viewport-h
                                         :dirty-tiles image-dirty-tiles})
        (promise/resolved (layer-impl/make-layer (cg/node-id this) canvas))
        (catch Throwable e
          (.close canvas)
          (throw e))))))

(defn make-raster-layer-node [vector-layer]
  (->VectorLayerNode vector-layer))

(defmethod graph/build-leaf :vector
  [atom _]
  (let [node (make-raster-layer-node atom)]
    {:nodes [node]
     :root  node}))
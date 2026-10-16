(ns top.kzre.krro.plugin.painting.core.schedule.view
  "视图相关节点"
  (:require
    [top.kzre.krro.core.util.computing-graph :as cg]
    [top.kzre.krro.core.util.promise  :as promise]
    [top.kzre.krro.plugin.painting.core.schedule.composite :as composite]
    [top.kzre.krro.plugin.painting.core.schedule.context :as context]
    [top.kzre.krro.plugin.painting.core.schedule.layer-impl :as layer-impl]
    [top.kzre.krro.plugin.painting.core.schedule.protocol :as proto])
  (:import
    (top.kzre.krro.core.util.computing_graph INode)
    (top.kzre.krro.util.math KMath)
    (top.kzre.krro.util.tile TiledCanvas)))

(defrecord NormalizeLayerTransformNode
  [layer-id ]
  cg/INode
  (node-id [_] (keyword (str NormalizeLayerTransformNode "-" layer-id)))
  (dependencies [_]
    [:context layer-id])
  (compute [this [ctx layer]]
    (let [transform (proto/transform layer)]
      (if (KMath/mat2dIsIdentity transform)
        (promise/resolved layer)
        (composite/composite-layers this [layer] ctx))))
  (release [_ _]))

(defn make-normalize-layer-transform-node
  "把图层变换归一化到标准变换"
  [^INode node]
  (->NormalizeLayerTransformNode (cg/node-id node) ))

(defrecord BlitCanvasLayerNode
  [^TiledCanvas backdrop layer-id]
  cg/INode
  (node-id [_] (keyword (str BlitCanvasLayerNode "-" layer-id)))
  (dependencies [_]
    [:context layer-id])
  (compute [this
            [{:keys [view-dirty-tiles image-tiles]
              :as ctx} layer]]
    (.deleteTiles backdrop view-dirty-tiles)
    (.keepTiles backdrop image-tiles)
    (if (and (.isEmpty (.getTiles ^TiledCanvas backdrop))
             (KMath/mat2dIsIdentity (proto/transform layer)))
      (promise/resolved layer)
      (composite/composite-layers
        this
        [(layer-impl/wrap-layer (cg/node-id this) backdrop)
         layer]
        (context/assoc-full-dirty-tiles ctx))))
  (release [_ _]))

(defn make-blit-canvas-layer-node
  "把图层变换归一化到标准变换"
  [^TiledCanvas backdrop ^INode node]
  (->BlitCanvasLayerNode backdrop (cg/node-id node)))

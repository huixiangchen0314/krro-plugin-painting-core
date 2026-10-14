(ns top.kzre.krro.plugin.painting.core.schedule.view
  "视图相关节点"
  (:require
    [top.kzre.krro.core.util.computing-graph :as cg]
    [top.kzre.krro.core.util.promise :as promise]
    [top.kzre.krro.plugin.painting.core.schedule.protocol :as proto]
    [top.kzre.krro.plugin.painting.core.schedule.composite :as composite])
  (:import (top.kzre.krro.core.util.computing_graph INode)
           (top.kzre.krro.util.math KMath)))

(defrecord NormalizeLayerTransformNode
  [layer-id]
  cg/INode
  (node-id [_] (keyword (str layer-id "-normalize-layer-transform-node")))
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
  (->NormalizeLayerTransformNode (cg/node-id node)))
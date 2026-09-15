(ns top.kzre.krro.plugin.painting.core.schedule.graph
  (:require
   [top.kzre.krro.core.util.computing-graph :as cg]
   [top.kzre.krro.plugin.painting.core.schedule.composite :as composite]
   [top.kzre.krro.plugin.painting.core.schedule.protocol :as proto]
   [top.kzre.krro.plugin.painting.core.schedule.raster-layer :as raster-layer]
   [top.kzre.krro.plugin.painting.core.schedule.result :as result]
   [top.kzre.krro.plugin.painting.core.schedule.util :as schedule.util]
   [top.kzre.krro.canvas.core.layer.group :as group]
   [top.kzre.krro.plugin.painting.core.schedule.context :as context])
  (:import
   (top.kzre.krro.core.util.computing_graph ComputingGraph)))


(defn build-graph
 ^ComputingGraph
  [layers {:keys [view-matrix] :as ctx}]
  (let [ctx-node (context/make-context-node ctx)
        composed (schedule.util/compose-transform layers view-matrix)
        throughed (mapv group/pass-through composed)
        layer-nodes     (mapv raster-layer/make-raster-layer-node throughed)
        composite-node  (composite/make-composite-node layer-nodes)
        result-node (result/make-result-node composite-node)
        nodes (into [ctx-node result-node
                     composite-node ]
                    layer-nodes)]
    (apply cg/graph nodes)))


(defn diff! [old-graph new-graph ctx-diff]
  (if-not old-graph
    new-graph
    new-graph))


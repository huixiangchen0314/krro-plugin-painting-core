(ns top.kzre.krro.plugin.painting.core.schedule.graph
  (:require [top.kzre.krro.plugin.painting.core.schedule.composite :as composite]
            [top.kzre.krro.plugin.painting.core.schedule.raster-layer :as raster-layer]
            [top.kzre.krro.plugin.painting.core.schedule.util :as schedule.util]))


(defn build-graph
  [layers {:keys [view-matrix]} ]
  (let [composed-layers (schedule.util/compose-transform layers view-matrix)
        layer-nodes     (mapv raster-layer/make-raster-layer-node composed-layers)]
    (composite/make-composite-node layer-nodes)))

(defn diff! [old-graph new-graph ctx-diff]
  (if-not old-graph
    new-graph
    new-graph))
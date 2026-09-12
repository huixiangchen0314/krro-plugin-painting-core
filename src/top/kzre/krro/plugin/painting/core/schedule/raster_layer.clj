(ns top.kzre.krro.plugin.painting.core.schedule.raster-layer
  (:require
    [top.kzre.krro.plugin.painting.core.schedule.protocol :as proto]))


(defrecord RasterLayer [layer]
  proto/IRenderNode
  (node-key [_] (:id layer))
  (request! [_ _]
    (proto/sync-result layer)))
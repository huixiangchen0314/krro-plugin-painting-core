(ns top.kzre.krro.plugin.painting.core.oplog.raster-layer
  (:require [top.kzre.krro.canvas.core.layer.util :as util]
            [top.kzre.krro.plugin.painting.core.change.raster-layer :as change]
            [top.kzre.krro.plugin.painting.core.oplog.oplog :as oplog]
            [top.kzre.krro.plugin.painting.core.record :as record])
  (:import (top.kzre.krro.util.tile TiledCanvas)))


(defonce ^:private replace-raster-layer-canvas-action-key* :replace-raster-layer-canvas)
(defn replace-raster-layer-canvas-action-key [] replace-raster-layer-canvas-action-key*)

(defn make-replace-raster-layer-canvas
  [layer-id canvas]
  {:type (replace-raster-layer-canvas-action-key)
   :transient true
   :layer-id layer-id
   :canvas canvas})

(defmethod oplog/apply-action! (replace-raster-layer-canvas-action-key)
  [{:keys [layer-id canvas]} record]
  (let [{:keys [layer layers layer-transform]}
        (record/layer-context record layer-id)
        layer-canvas (:canvas layer)
        new-layer (assoc layer :canvas canvas)
        new-layers (util/replace-layer new-layer layers)
        dirties (into (set (.getTiles ^TiledCanvas layer-canvas))
                      (set (.getTiles ^TiledCanvas canvas)))]
    (.close layer-canvas)
    [(assoc-in record [:canvas-data :layers] new-layers)
     (change/make-raster-layer-dirty layer-id (.copy canvas) dirties layer-transform)]))
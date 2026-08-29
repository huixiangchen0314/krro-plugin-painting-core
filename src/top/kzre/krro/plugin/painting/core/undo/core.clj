(ns top.kzre.krro.plugin.painting.core.undo.core
  (:require
    [top.kzre.krro.plugin.painting.core.ops.undo :as core]
    [top.kzre.krro.plugin.painting.core.undo.add-raster-layer :as add-raster-layer]
    [top.kzre.krro.plugin.painting.core.undo.edit-canvas :as edit-canvas]))

(def record-canvas-edited! edit-canvas/record-canvas-edited!)
(def record-raster-layer-added! add-raster-layer/record-raster-layer-added!)
(def record-raster-layer-edited! core/record-raster-layer-updated!)
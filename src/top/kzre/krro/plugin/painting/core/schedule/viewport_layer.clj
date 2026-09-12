(ns top.kzre.krro.plugin.painting.core.schedule.viewport-layer
  (:require
    [top.kzre.krro.plugin.painting.core.schedule.protocol :as proto]
    [top.kzre.krro.canvas.core.layer.util :as util])
  (:import (top.kzre.krro.util.tile TiledCanvas)))


(defrecord ViewportLayer
  [id
   ^TiledCanvas canvas
   ^boolean visible
   ^float opacity
   blend-mode]
  proto/ILayer
  (layer-id [_] id)
  (canvas [_] canvas)
  (transform [_] util/identity-matrix)
  (visible? [_] visible)
  (layer-opacity [_] opacity)
  (blend-mode [_] blend-mode))
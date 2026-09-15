(ns top.kzre.krro.plugin.painting.core.schedule.viewport-layer
  (:require
    [top.kzre.krro.plugin.painting.core.schedule.protocol :as proto]
    [top.kzre.krro.canvas.core.layer.util :as util])
  (:import (java.util UUID)
           (top.kzre.krro.util.tile TiledCanvas)))


(defrecord ViewportLayer
  [id
   ^TiledCanvas canvas
   ^boolean visible
   ^float opacity
   blend-mode]
  proto/ILayer
  (layer-id [_] id)
  (canvas [_] canvas)
  (visible? [_] visible)
  (transform [_] util/identity-matrix)
  (opacity [_] opacity)
  (blend-mode [_] blend-mode))


(defn make-viewport-layer
  ([id canvas] (make-viewport-layer id canvas true 1.0 :normal))
  ([id canvas opacity ] (make-viewport-layer id canvas true opacity :normal))
  ([id canvas opacity blend-mode] (make-viewport-layer id canvas true opacity blend-mode))
  ([id canvas visible opacity blend-mode]
   (->ViewportLayer
     id
     canvas visible opacity blend-mode)))
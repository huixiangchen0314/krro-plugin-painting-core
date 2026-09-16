(ns top.kzre.krro.plugin.painting.core.schedule.layer-impl
  (:require
   [top.kzre.krro.canvas.core.layer.util :as util]
   [top.kzre.krro.plugin.painting.core.schedule.protocol :as proto])
  (:import
   [java.lang AutoCloseable]
   (top.kzre.krro.util.tile TiledCanvas)))


(defrecord Layer
  [id
   ^TiledCanvas canvas
   ^floats transform
   ^boolean visible
   ^float opacity
   blend-mode]
  proto/ILayer
  (layer-id [_] id)
  (canvas [_] canvas)
  (transform [_] transform)
  (visible? [_] visible)
  (opacity [_] opacity)
  (blend-mode [_] blend-mode)
  AutoCloseable
  (close [_] (.close canvas)))


(defn make-layer
  ([id canvas] (make-layer id canvas util/identity-matrix true 1.0 :normal))
  ([id canvas transform] (make-layer id canvas transform true 1.0 :normal))
  ([id canvas transform visible] (make-layer id canvas transform visible 1.0 :normal))
  ([id canvas transform visible opacity ] (make-layer id canvas transform visible opacity :normal))
  ([id canvas transform visible opacity blend-mode]
   (->Layer id canvas transform visible opacity blend-mode)))
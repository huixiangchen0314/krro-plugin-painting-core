(ns top.kzre.krro.plugin.painting.core.model.tiled-image
  (:import (top.kzre.krro.util.tile TiledCanvas)))


(defrecord TiledImage
  [^TiledCanvas canvas
   ^int width
   ^int height])
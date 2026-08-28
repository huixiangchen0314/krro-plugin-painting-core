(ns top.kzre.krro.plugin.painting.core.layer.dispose
  (:import (top.kzre.krro.util.tile TiledCanvas)))

(defmulti dispose-layer :type)

(defmethod dispose-layer :default [layer] layer)

(defmethod dispose-layer :raster
 [layer]
 (when-let [^TiledCanvas canvas (:canvas layer)]
   (.clear canvas)))

(defmethod dispose-layer :group
  [layer]
  (doseq [l (:layers layer)]
    (dispose-layer l)))
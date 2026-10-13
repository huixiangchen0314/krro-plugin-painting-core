(ns top.kzre.krro.plugin.painting.core.layer.dispose
  (:require [taoensso.timbre :as log])
  (:import (top.kzre.krro.util.tile TiledCanvas)))

(defmulti dispose-layer :type)

(defmethod dispose-layer :default [layer] layer)

(defmethod dispose-layer :raster
 [layer]
 (when-let [^TiledCanvas canvas (:canvas layer)]
   (.close canvas)))

(defmethod dispose-layer :group
  [layer]
  (doseq [l (:layers layer)]
    (dispose-layer l)))


(defn dispose-layers! [layers]
  (doseq [layer layers]
    (try
      (dispose-layer layer)
      (catch Throwable t
        (log/error t "dispose cloned layer failed"
                   {:layer-id (:id layer)})))))
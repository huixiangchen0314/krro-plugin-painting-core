(ns top.kzre.krro.plugin.painting.core.layer.destroy
  (:require [top.kzre.krro.plugin.painting.core.layer.dispose :as dispose]
            [top.kzre.krro.plugin.painting.core.project.raster-layer :as pr]))


(defmulti destroy-layer :type)

(defmethod destroy-layer :default [layer] (dispose/dispose-layer layer))

(defmethod destroy-layer :raster
 [layer]
  (pr/delete-raster! (:id layer))
  (dispose/dispose-layer layer))

(defmethod destroy-layer :group
 [layer]
 (doseq [l (:layers layer)]
   (destroy-layer l)))
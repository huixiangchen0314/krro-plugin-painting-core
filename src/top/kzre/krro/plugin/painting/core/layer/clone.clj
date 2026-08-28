(ns top.kzre.krro.plugin.painting.core.layer.clone
  (:import (top.kzre.krro.util.tile TiledCanvas)))

(defmulti clone-layer :type)

;; 默认是不可变map，返回自己
(defmethod clone-layer :default [layer] layer)

;; 光栅图层拷贝画布
(defmethod clone-layer  :raster [layer]
  (let [canvas (:canvas layer)]
    (assoc layer :canvas
                 (doto (TiledCanvas. (.getTileSize canvas)
                                     (.getDefaultPixel canvas))
                   (.shareFrom canvas)))))


(defmethod clone-layer :group
  [layer]
  (update layer :layers
          (fn [ls]
            (mapv clone-layer ls))))
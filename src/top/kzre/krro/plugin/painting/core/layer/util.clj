(ns top.kzre.krro.plugin.painting.core.layer.util
  (:require
   [top.kzre.krro.canvas.core.layer.core :as lc]))


(defn insert-layer-at
  [cd path layer]
  (let [new-layers (lc/insert-layer path layer (:layers cd))
        new-cd     (assoc cd :layers new-layers)]
    new-cd))
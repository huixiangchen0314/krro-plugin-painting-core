(ns top.kzre.krro.plugin.painting.core.edit.common
  (:require [top.kzre.krro.canvas.core.layer.util :as util]
            [top.kzre.krro.plugin.painting.core.viewport :as vp]))


(defn layer-point->screen [point layer-transform viewport]
  (let [world-pos (util/transform-point layer-transform (:x point) (:y point))]
    (vp/logic->screen viewport (:x world-pos) (:y world-pos))))
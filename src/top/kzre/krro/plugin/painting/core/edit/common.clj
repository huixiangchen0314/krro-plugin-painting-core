(ns top.kzre.krro.plugin.painting.core.edit.common
  (:require [top.kzre.krro.canvas.core.layer.util :as util]
            [top.kzre.krro.plugin.painting.core.edit.protocol :as p]
            [top.kzre.krro.plugin.painting.core.viewport :as vp]))

(defn cleanup-tool-data! [record]
  (let [tool-data (get-in record [:canvas-state :tool-data])]
    (when (and tool-data
               (satisfies? p/IToolData tool-data))
      (p/cleanup! tool-data))
    (assoc-in record [:canvas-state :tool-data] {})))


(defn layer-point->screen [point layer-transform viewport]
  (let [world-pos (util/transform-point layer-transform (:x point) (:y point))]
    (vp/logic->screen viewport (:x world-pos) (:y world-pos))))
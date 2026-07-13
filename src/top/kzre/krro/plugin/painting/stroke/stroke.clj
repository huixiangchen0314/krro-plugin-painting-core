(ns top.kzre.krro.plugin.painting.stroke.stroke
  (:require
   [top.kzre.krro.canvas.core.canvas.protocol :as cp])
  (:import
    (top.kzre.krro.canvas.core Arrays)
    (top.kzre.krro.plugin.painting.canvas.state CanvasRuntime)))

(defmulti backup-layer!
          "在开始对图层进行操作前备份图层数据."
          (fn [layer ^CanvasRuntime _runtime] (:type layer)))

(defmethod backup-layer! :raster
  [layer ^CanvasRuntime runtime]
  (let [buf (:layer-buffer runtime)
        pixels (cp/data (:canvas layer))]
    (Arrays/copy pixels buf))
  runtime)

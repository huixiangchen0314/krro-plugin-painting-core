(ns top.kzre.krro.plugin.painting.core.ops.backup
  (:require
    [top.kzre.krro.util.tiled-canvas :as tc]
    [top.kzre.krro.plugin.painting.core.state])
  (:import
    (top.kzre.krro.plugin.painting.core.state CanvasRuntime)))

(defmulti backup-layer!
          "在开始对图层进行操作前备份图层数据."
          (fn [layer ^CanvasRuntime _runtime] (:type layer)))

(defmethod backup-layer! :raster
  [layer ^CanvasRuntime runtime]
  (assoc runtime :layer-backup (tc/deep-copy (:canvas layer))))


(defmethod backup-layer! :vector
  [layer ^CanvasRuntime runtime]
  (assoc runtime :layer-backup layer))
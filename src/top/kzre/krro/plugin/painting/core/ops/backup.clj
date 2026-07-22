(ns top.kzre.krro.plugin.painting.core.ops.backup
  (:require
    [top.kzre.krro.plugin.painting.core.state])
  (:import
    (top.kzre.krro.plugin.painting.core.state CanvasRuntime)
    (top.kzre.krro.util.tile TiledCanvas)))

(defmulti backup-layer!
          (fn [layer ^CanvasRuntime _runtime] (:type layer)))

(defmulti release-backup!
          (fn [layer ^CanvasRuntime _runtime] (:type layer)))

(defmethod release-backup! :default [_layer ^CanvasRuntime _runtime])

(defmethod backup-layer! :raster
  [layer ^CanvasRuntime runtime]
  (let [canvas ^TiledCanvas (:canvas layer)]
    (assoc runtime :layer-backup {:type :raster
                                  :canvas (doto (TiledCanvas. (.getTileSize canvas)
                                                              (.getDefaultPixel canvas))
                                            (.shareFrom canvas))})))

(defmethod release-backup! :raster
  [_layer ^CanvasRuntime _runtime]
  ;; 从运行时中取出备份，清空备份画布并移除引用
  (when-let [backup (:layer-backup _runtime)]
    (when-let [backup-canvas (:canvas backup)]
      (.clear ^TiledCanvas backup-canvas))
    (assoc _runtime :layer-backup nil)))

(defmethod backup-layer! :vector
  [layer ^CanvasRuntime runtime]
  (assoc runtime :layer-backup layer))
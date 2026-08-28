(ns top.kzre.krro.plugin.painting.core.ops.backup
  (:require
    [top.kzre.krro.plugin.painting.core.state])
  (:import
    (top.kzre.krro.plugin.painting.core.state CanvasState)
    (top.kzre.krro.util.tile TiledCanvas)))

(defmulti backup-layer!
          (fn [layer ^CanvasState _runtime] (:type layer)))

(defmulti release-backup!
          (fn [^CanvasState state] (:type (:layer-backup state))))

(defmethod release-backup! :default [^CanvasState state] state)


;; 默认图层为edn，直接备份
(defmethod backup-layer! :default
  [layer ^CanvasState runtime]
  (assoc runtime :layer-backup layer))

;; 光栅图层有画布数据，需要手动维护
(defmethod backup-layer! :raster
  [layer ^CanvasState runtime]
  (let [canvas ^TiledCanvas (:canvas layer)]
    (assoc runtime :layer-backup (assoc layer :canvas
                                              (doto (TiledCanvas. (.getTileSize canvas)
                                                                  (.getDefaultPixel canvas))
                                                (.shareFrom canvas))))))

(defmethod release-backup! :raster
  [^CanvasState runtime]
  ;; 从运行时中取出备份，清空备份画布并移除引用
  (when-let [backup (:layer-backup runtime)]
    (when-let [backup-canvas (:canvas backup)]
      (.clear ^TiledCanvas backup-canvas))
    (assoc runtime :layer-backup nil)))


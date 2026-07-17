(ns top.kzre.krro.plugin.painting.core.ops.replace
  (:require
    [top.kzre.krro.plugin.painting.core.ops.undo :as undo]))


(defmulti replace-layer!
          "执行图层替换并记录 undo。返回 new-layer。"
          (fn [_canvas-id _path _old-layer new-layer] (:type new-layer)))

(defmethod replace-layer! :raster
  [canvas-id path old-layer new-layer]
  ;; 运行时不再维护侧表，仅记录撤销；侧表在 persistable-layer! 时才会更新。
  (undo/record-raster-layer-replace! canvas-id path old-layer new-layer)
  new-layer)

;; 矢量图层所有数据可序列化
(defmethod replace-layer! :vector
  [canvas-id path old-layer new-layer]
  new-layer)
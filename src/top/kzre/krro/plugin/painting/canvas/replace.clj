(ns top.kzre.krro.plugin.painting.canvas.replace
  (:require
    [top.kzre.krro.canvas.core.canvas.protocol :as cp]
    [top.kzre.krro.core.core :as kcc]
    [top.kzre.krro.plugin.painting.canvas.undo :as undo]
    [top.kzre.krro.plugin.painting.project.raster-layer :as pr]))


(defmulti replace-layer!
          "执行图层替换并记录 undo。返回 new-layer。"
          (fn [_canvas-id _path _old-layer new-layer] (:type new-layer)))

(defmethod replace-layer! :raster
  [canvas-id path old-layer new-layer]
  (let [old-pixels (cp/data (:canvas old-layer))
        new-pixels (cp/data (:canvas new-layer))
        layer-id   (:id new-layer)]
    (when (not (identical? old-pixels new-pixels))
      (if (kcc/select-by-id :krro.painting/raster layer-id)
        (kcc/update-by-id! :krro.painting/raster layer-id #(assoc % :data new-pixels))
        (pr/create-raster! layer-id canvas-id new-pixels))))
  (undo/record-raster-layer-replace! canvas-id path old-layer new-layer)
  new-layer)
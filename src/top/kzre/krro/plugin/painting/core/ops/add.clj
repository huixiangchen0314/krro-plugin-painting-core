(ns top.kzre.krro.plugin.painting.core.ops.add
  "图层添加多方法：执行添加图层的全部副作用，包括项目更新、侧表维护、刷新与撤销记录。"
  (:require
    [top.kzre.krro.canvas.raster.core :as raster]
    [top.kzre.krro.canvas.vector.core :as vector]
    [top.kzre.krro.core.hook :as hook]
    [top.kzre.krro.plugin.painting.core.ops.layer :as layer]
    [top.kzre.krro.plugin.painting.core.ops.undo :as undo]
    [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
    [top.kzre.krro.plugin.painting.core.project.raster-layer :as pr]
    [top.kzre.krro.plugin.painting.core.spec :as spec]
    [top.kzre.krro.plugin.painting.core.state :as state]))

;; 分派键：第一个参数 layer-type
(defmulti add-layer!
          "添加图层到画布。layer-type → {:layer :layer-id :path}
           (add-layer! :raster canvas-id path)"
          (fn [layer-type _canvas-id _path] layer-type))

;; ── 光栅图层 ──────────────────────────────────
(defmethod add-layer! :raster [_ canvas-id path]
  (let [new-layer (raster/make-raster-layer pc/global-tile-size)
        cd (pc/canvas-data! canvas-id)
        {:keys [canvas-data layer-id]} (layer/add-layer-at cd path new-layer)]
    (layer/update-project! canvas-id canvas-data)
    ;; 侧表数据是权威的，始终维持
    (pr/create-raster! layer-id canvas-id (:canvas new-layer))
    (state/invalidate-canvas-dirty! canvas-id)
    (layer/refresh-canvas-frames! canvas-id)
    (layer/set-selected-layer-id! canvas-id layer-id)
    (undo/record-raster-layer-add! canvas-id path new-layer)
    (hook/run-hook! spec/layer-changed-hook-key canvas-id)
    {:layer new-layer :layer-id layer-id :path path}))

;; ── 矢量图层 ──────────────────────────────────
(defmethod add-layer! :vector [_ canvas-id path]
  (let [new-layer (vector/make-vector-layer)
        cd (pc/canvas-data! canvas-id)
        {:keys [canvas-data layer-id]} (layer/add-layer-at cd path new-layer)]
    (layer/update-project! canvas-id canvas-data)
    (state/invalidate-canvas-dirty! canvas-id)
    (layer/refresh-canvas-frames! canvas-id)
    (layer/set-selected-layer-id! canvas-id layer-id)
    ;; 空图层，只更新图层显示，不刷新画布
    (undo/record-layer-edit-attrs-state! canvas-id)
    (hook/run-hook! spec/layer-changed-hook-key canvas-id)
    {:layer new-layer :layer-id layer-id :path path}))
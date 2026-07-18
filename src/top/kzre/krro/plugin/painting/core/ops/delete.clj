(ns top.kzre.krro.plugin.painting.core.ops.delete
  "图层删除多方法：执行删除图层的全部副作用，包括侧表清理、刷新与撤销记录。"
  (:require
    [top.kzre.krro.plugin.painting.core.ops.layer :as layer]
    [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
    [top.kzre.krro.plugin.painting.core.project.raster-layer :as pr]
    [top.kzre.krro.plugin.painting.core.state :as state]
    [top.kzre.krro.plugin.painting.core.ops.undo :as undo]
    [top.kzre.krro.plugin.painting.core.spec :as spec]
    [top.kzre.krro.core.hook :as hook]))

;; 分派键：第一个参数 removed（图层对象），根据 (:type removed) 分派
(defmulti delete-layer!
          "删除图层。调用：(delete-layer! removed canvas-id path)"
          (fn [removed _canvas-id _path] (:type removed)))

;; ── 光栅图层 ──────────────────────────────────
(defmethod delete-layer! :raster [removed canvas-id path]
  (let [cd (pc/canvas-data! canvas-id)
        selected-id (pc/selected-layer-id canvas-id)       ;; 从项目数据获取选中ID
        {:keys [canvas-data _removed layer-id new-selected-id]} (layer/remove-layer-at cd selected-id path)]
    (layer/update-project! canvas-id canvas-data)
    (pr/delete-raster! layer-id)
    (state/invalidate-canvas-dirty! canvas-id)
    (layer/refresh-canvas-frames! canvas-id)
    (when new-selected-id (layer/set-selected-layer-id! canvas-id new-selected-id))
    (undo/record-raster-layer-remove! canvas-id path removed)
    (hook/run-hook! spec/layer-changed-hook-key canvas-id)
    {:removed removed :layer-id layer-id :new-selected-id new-selected-id}))

;; ── 矢量图层 ──────────────────────────────────
(defmethod delete-layer! :vector [removed canvas-id path]
  (let [cd (pc/canvas-data! canvas-id)
        selected-id (pc/selected-layer-id canvas-id)
        {:keys [canvas-data _removed layer-id new-selected-id]} (layer/remove-layer-at cd selected-id path)]
    (layer/update-project! canvas-id canvas-data)
    (state/invalidate-canvas-dirty! canvas-id)
    (layer/refresh-canvas-frames! canvas-id)
    (when new-selected-id (layer/set-selected-layer-id! canvas-id new-selected-id))
    (undo/record-layer-render-attrs-state! canvas-id)
    (hook/run-hook! spec/layer-changed-hook-key canvas-id)
    {:removed removed :layer-id layer-id :new-selected-id new-selected-id}))
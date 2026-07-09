(ns top.kzre.krro.plugin.painting.commands
  "绘画插件的命令注册，封装图层、笔刷等操作为 Krrō 命令。"
  (:require [top.kzre.krro.core.command :as cmd]
            [top.kzre.krro.core.frame :as frame]
            [top.kzre.krro.plugin.painting.spec :as spec]
            [top.kzre.krro.plugin.painting.canvas.core]
            [top.kzre.krro.plugin.painting.canvas.layer :as layer]
            [top.kzre.krro.plugin.painting.canvas.brush :as brush]))

;; ── 辅助函数 ──────────────────────────────────────
(defn- current-canvas-id
  "从当前 Frame 获取正在编辑的画布 ID。"
  []
  (frame/param frame/*current-frame* spec/canvas-id-key))

(defn- current-runtime
  "从当前 Frame 获取画布运行时状态。"
  []
  (frame/param frame/*current-frame* spec/canvas-runtime-key))

;; ── 图层命令 ──────────────────────────────────────
(cmd/register-command!
  :krro.painting/add-raster-layer
  (fn [project]
    (when-let [rt (current-runtime)]
      (let [canvas-id (current-canvas-id)]
        (layer/add-layer! rt canvas-id)))
    @project)
  :description "在画布顶部添加一个新的光栅图层")

(cmd/register-command!
  :krro.painting/duplicate-layer
  (fn [project]
    (when-let [rt (current-runtime)]
      (let [canvas-id (current-canvas-id)
            selected-id (layer/get-selected-layer-id rt)]
        (when selected-id
          (layer/duplicate-layer! rt canvas-id selected-id))))
    @project)
  :description "复制当前选中的图层")

(cmd/register-command!
  :krro.painting/remove-layer
  (fn [project]
    (when-let [rt (current-runtime)]
      (let [canvas-id (current-canvas-id)
            selected-id (layer/get-selected-layer-id rt)]
        (when selected-id
          (layer/remove-layer! rt canvas-id selected-id))))
    @project)
  :description "删除当前选中的图层")

(cmd/register-command!
  :krro.painting/select-previous-layer
  (fn [project]
    (when-let [rt (current-runtime)]
      (let [layers (layer/get-layers rt)
            current-id (layer/get-selected-layer-id rt)]
        (when-let [idx (first (keep-indexed #(when (= (:id %2) current-id) %1) layers))]
          (let [new-idx (max 0 (dec idx))]
            (layer/set-selected-layer-id! rt (:id (nth layers new-idx)))))))
    @project)
  :description "选择上一个图层（索引减 1）")

(cmd/register-command!
  :krro.painting/select-next-layer
  (fn [project]
    (when-let [rt (current-runtime)]
      (let [layers (layer/get-layers rt)
            current-id (layer/get-selected-layer-id rt)]
        (when-let [idx (first (keep-indexed #(when (= (:id %2) current-id) %1) layers))]
          (let [new-idx (min (dec (count layers)) (inc idx))]
            (layer/set-selected-layer-id! rt (:id (nth layers new-idx)))))))
    @project)
  :description "选择下一个图层（索引加 1）")

(cmd/register-command!
  :krro.painting/toggle-layer-locked
  (fn [project]
    (when-let [rt (current-runtime)]
      (let [canvas-id (current-canvas-id)
            layer-id (layer/get-selected-layer-id rt)]
        (when layer-id
          ;; 这里需要引入 layer-meta 命名空间来操作锁定状态
          ;; 但我们可以在 layer-meta 中提供一个 toggle 函数
          )))
    @project)
  ;; 留待实现，需组合 layer-meta 函数
  )

(cmd/register-command!
  :krro.painting/set-global-brush
  (fn [project new-brush]
    (brush/set-global-brush! new-brush)
    @project)
  :description "设置全局笔刷（参数为笔刷 map）")
(ns top.kzre.krro.plugin.painting.commands
  "绘画插件的命令注册，封装图层、笔刷等操作为 Krrō 命令。"
  (:require
   [top.kzre.krro.core.command :as cmd]
   [top.kzre.krro.core.frame :as frame]
   [top.kzre.krro.plugin.painting.canvas.brush :as brush]
   [top.kzre.krro.plugin.painting.canvas.layer :as layer] ;; 基础函数
   [top.kzre.krro.plugin.painting.canvas.layer-undo :as layer-undo] ;; 带撤销的函数
   [top.kzre.krro.plugin.painting.canvas.state :as state]
   [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
   [top.kzre.krro.plugin.painting.spec :as spec]))

(defn- current-canvas-id []
  (frame/param frame/*current-frame* spec/canvas-id-key))

;; ── 图层命令 ──────────────────────────────────────
(cmd/register-command!
  :krro.painting/add-raster-layer
  (fn [project]
    (layer-undo/add-raster-layer-over-selected-undo! (current-canvas-id))
    @project)
  :description "在选中图层上方添加一个新的光栅图层")

(cmd/register-command!
  :krro.painting/duplicate-layer
  (fn [_project]
    (let [canvas-id (current-canvas-id)
          selected-id (state/selected-layer-id canvas-id)]
      (when selected-id
        (layer-undo/duplicate-layer-undo! canvas-id selected-id))))
  :description "复制当前选中的图层（可撤销）")


(cmd/register-command!
  :krro.painting/remove-layer
  (fn [project]
    (let [canvas-id (current-canvas-id)]
      (when-let [path (layer/selected-layer-path canvas-id)]
        (layer-undo/remove-layer-at-undo! canvas-id path)))
    @project)
  :description "删除当前选中的图层（可撤销）")

(cmd/register-command!
  :krro.painting/select-previous-layer
  (fn [project]
    (let [canvas-id (current-canvas-id)
          layers (pc/layers-by-id! canvas-id)
          current-id (state/selected-layer-id canvas-id)]
      (when-let [idx (first (keep-indexed #(when (= (:id %2) current-id) %1) layers))]
        (let [new-idx (max 0 (dec idx))]
          (layer/set-selected-layer-id! canvas-id (:id (nth layers new-idx))))))
    @project)
  :description "选择上一个图层（索引减 1）")

(cmd/register-command!
  :krro.painting/select-next-layer
  (fn [project]
    (let [canvas-id (current-canvas-id)
          layers (pc/layers-by-id! canvas-id)
          current-id (state/selected-layer-id canvas-id)]
      (when-let [idx (first (keep-indexed #(when (= (:id %2) current-id) %1) layers))]
        (let [new-idx (min (dec (count layers)) (inc idx))]
          (layer/set-selected-layer-id! canvas-id (:id (nth layers new-idx))))))
    @project)
  :description "选择下一个图层（索引加 1）")

(cmd/register-command!
  :krro.painting/toggle-layer-locked
  (fn [project]
    ;; 待实现：调用 layer-meta 中的切换锁定函数
    @project)
  :description "切换当前图层的锁定状态")

(cmd/register-command!
  :krro.painting/set-global-brush
  (fn [project new-brush]
    (brush/set-global-brush! new-brush)
    @project)
  :description "设置全局笔刷（参数为笔刷 map）")
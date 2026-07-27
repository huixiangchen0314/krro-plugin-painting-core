(ns top.kzre.krro.plugin.painting.core.ops.layer-undo
  "图层操作. 基本上都是通过路径操作, 提供根据 id 查找路径的 Api，内部不对这个进行封装.
  编辑器自己做controller 的封装."
  (:require
    [top.kzre.krro.canvas.core.layer.core :as lc]
    [top.kzre.krro.plugin.painting.core.ops.add :as add]
    [top.kzre.krro.plugin.painting.core.ops.delete :as delete]
    [top.kzre.krro.plugin.painting.core.ops.duplicate :as duplicate]
    [top.kzre.krro.plugin.painting.core.ops.layer :as layer]
    [top.kzre.krro.plugin.painting.core.ops.undo :as undo]
    [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
    [top.kzre.krro.plugin.painting.core.state :as state]))

;; ── 辅助：计算在选中图层上方插入的路径 ──────────
(defn- above-path
  "返回在 selected-id 上方插入新图层时应使用的路径。"
  [layers selected-id]
  (if-let [raw-path (lc/find-layer-path selected-id layers)]
    (let [parent (butlast raw-path)
          idx (last raw-path)]
      (conj (vec parent) (inc idx)))
    [(count layers)]))


(defn add-raster-layer-over-selected-undo! [canvas-id]
  (let [selected-id (pc/current-layer-id canvas-id)       ;; 从项目数据获取
        layers      (pc/layers-by-id! canvas-id)
        path        (above-path layers selected-id)]
    (add/add-layer! :raster canvas-id path )))

(defn add-vector-layer-over-selected-undo! [canvas-id]
  (let [selected-id (pc/current-layer-id canvas-id)
        layers      (pc/layers-by-id! canvas-id)
        path        (above-path layers selected-id)]
    (add/add-layer! :vector canvas-id path )))



(defn remove-layer-at-undo! [canvas-id path]
  (let [layers  (pc/layers-by-id! canvas-id)
        removed (lc/find-layer-by-path path layers)]
    (when removed
      (delete/delete-layer! removed canvas-id path))))

(defn remove-layer-undo! [canvas-id layer-id]
  (when-let [path (lc/find-layer-path layer-id (pc/layers-by-id! canvas-id))]
    (remove-layer-at-undo! canvas-id path)))

(defn remove-selected-layer-undo! [canvas-id]
  (when-let [path (layer/current-layer-path canvas-id)]
    (remove-layer-at-undo! canvas-id path)))



(defn duplicate-layer-undo! [canvas-id layer-id]
  (let [layers (pc/layers-by-id! canvas-id)
        layer  (lc/find-layer layer-id layers)]
    (when layer
      (duplicate/duplicate-layer! canvas-id layer))))

(defn duplicate-selected-layer-undo! [canvas-id]
  (when-let [layer-id (pc/current-layer-id canvas-id)]
    (duplicate-layer-undo! canvas-id layer-id)))



(defn move-layer-undo! [canvas-id old-path new-path]
  (layer/move-layer! canvas-id old-path new-path)
  (undo/record-layer-render-attrs-state! canvas-id))

(defn update-layer-at-undo! [canvas-id path updater]
  (layer/update-layer-at! canvas-id path updater)
  (undo/record-layer-render-attrs-state! canvas-id))

(defn update-layer-by-id-undo! [canvas-id layer-id updater]
  (let [cd (pc/canvas-data! canvas-id)
        path (lc/find-layer-path layer-id (:layers cd))]
    (when path
      (update-layer-at-undo! canvas-id path updater))))

(defn commit-layer-undo! [canvas-id layer old-state new-state]
  (let [layer-id (:id layer)
        cd (pc/canvas-data! canvas-id)
        layers (:layers cd)
        path (lc/find-layer-path layer-id layers)]
    (when path
      (layer/update-layer-at! canvas-id path (fn [_] layer))
      (undo/record-layer-commit! canvas-id old-state new-state)
      (swap! state/canvas-runtimes assoc canvas-id new-state))))

(defn toggle-layer-visibility! [canvas-id layer-id]
  (letfn [(updator [l] (assoc l :visible? (not (:visible? l))))]
    (when (layer/update-layer-by-id! canvas-id layer-id updator)
      (undo/record-layer-render-attrs-state! canvas-id))))

(defn set-layer-visibility! [canvas-id layer-id visible?]
  (letfn [(updator [l] (assoc l :visible? visible?))]
    (when (layer/update-layer-by-id! canvas-id layer-id updator)
      (undo/record-layer-render-attrs-state! canvas-id))))

(defn update-selected-layer-undo! [canvas-id updater]
  (when-let [selected-id (pc/current-layer-id canvas-id)]
    (update-layer-by-id-undo! canvas-id selected-id updater)))
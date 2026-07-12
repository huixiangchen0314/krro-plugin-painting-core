(ns top.kzre.krro.plugin.painting.canvas.layer-undo
  "图层操作的撤销记录版本。封装 layer 副作用函数并记录 undo 状态。"
  (:require
    [top.kzre.krro.canvas.core.layer.core :as layer-core]
    [top.kzre.krro.plugin.painting.canvas.layer :as layer]
    [top.kzre.krro.plugin.painting.canvas.project :as proj]
    [top.kzre.krro.plugin.painting.canvas.state :as state]
    [top.kzre.krro.plugin.painting.canvas.undo :as undo]))

;; ── 添加图层 ──────────────────────────────────────

(defn add-raster-layer-over-selected-undo! [canvas-id]
  (let [result (layer/add-raster-layer-over-selected! canvas-id)   ;; 副作用函数返回完整 map
        {:keys [layer path]} result]
    (undo/record-raster-layer-add! canvas-id path layer)
    result))

(defn add-layer-at-undo! [canvas-id path layer]
  (let [result (layer/add-layer-at! canvas-id path layer)
        {:keys [layer]} result]
    (undo/record-raster-layer-add! canvas-id path layer)
    result))

;; ── 删除图层 ──────────────────────────────────────

(defn remove-layer-at-undo! [canvas-id path]
  (let [cd (proj/canvas-data! canvas-id)
        removed (layer-core/find-layer-by-path path (:layers cd))]
    (layer/remove-layer-at! canvas-id path)
    (undo/record-raster-layer-remove! canvas-id path removed)))

;; ── 移动图层 ──────────────────────────────────────

(defn move-layer-undo! [canvas-id old-path new-path]
  (layer/move-layer! canvas-id old-path new-path)
  (undo/record-state! canvas-id))

;; ── 复制图层 ──────────────────────────────────────

(defn duplicate-layer-undo! [canvas-id layer-id]
  (let [result (layer/duplicate-layer! canvas-id layer-id)
        {:keys [layer path]} result]
    (undo/record-raster-layer-add! canvas-id path layer)
    result))

;; ── 更新图层 ──────────────────────────────────────

(defn update-layer-at-undo! [canvas-id path updater]
  (layer/update-layer-at! canvas-id path updater)
  (undo/record-state! canvas-id))

(defn update-layer-by-id-undo! [canvas-id layer-id updater]
  (let [cd (proj/canvas-data! canvas-id)
        path (layer-core/find-layer-path layer-id (:layers cd))]
    (when path
      (update-layer-at-undo! canvas-id path updater))))

(defn toggle-layer-visibility! [canvas-id layer-id]
  (letfn [(updator [layer]
            (let [visible? (:visible? layer)]
              (assoc layer :visible? (not visible?))))]
    (when (layer/update-layer-by-id! canvas-id layer-id updator)
      (undo/record-visibility-state! canvas-id))))

(defn set-layer-visibility! [canvas-id layer-id visible?]
  (letfn [(updator [layer] (assoc layer :visible? visible?))]
    (when (layer/update-layer-by-id! canvas-id layer-id updator)
      (undo/record-visibility-state! canvas-id))))
(defn update-selected-layer-undo! [canvas-id updater]
  (when-let [selected-id (state/selected-layer-id canvas-id)]
    (update-layer-by-id-undo! canvas-id selected-id updater)))
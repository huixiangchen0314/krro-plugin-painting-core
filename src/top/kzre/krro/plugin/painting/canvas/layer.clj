(ns top.kzre.krro.plugin.painting.canvas.layer
  "图层操作：纯函数、副作用函数与带撤销函数。
   基于路径管理嵌套图层组，自动同步到项目原子。"
  (:require
   [top.kzre.krro.canvas.core.canvas.protocol :as cp]
   [top.kzre.krro.canvas.core.layer.core :as lc]
   [top.kzre.krro.canvas.raster.core :as rl]
   [top.kzre.krro.core.core :as kcc]
   [top.kzre.krro.core.frame :as frame]
   [top.kzre.krro.core.hook :as hook]
   [top.kzre.krro.plugin.painting.canvas.state :as state]
   [top.kzre.krro.plugin.painting.project.canvas :as pc]
   [top.kzre.krro.plugin.painting.project.layer-meta :as pm]
   [top.kzre.krro.plugin.painting.project.raster-layer :as pr]
   [top.kzre.krro.plugin.painting.spec :as spec])
  (:import
   (javafx.application Platform)
   (top.kzre.krro.plugin.painting.project.canvas CanvasData)))

;; ── 工具函数 ──────────────────────────────────────
(defn- with-layers [^CanvasData old-cd new-layers]
  (CanvasData. (:id old-cd) (:width old-cd) (:height old-cd) (vec new-layers)))

(defn update-project! [canvas-id new-cd]
  (kcc/update-by-id! :krro.painting/canvas canvas-id (constantly new-cd)))

(defn refresh-canvas-frames!
  "重新渲染画布并通知所有相关 Frame 上传。"
  [canvas-id]
  (when-let [rt (state/canvas-runtime canvas-id)]
    (let [preview (state/preview-buffer rt)
          [w h]   (pc/canvas-size canvas-id)]
      (state/render-canvas! canvas-id preview)
      (doseq [f (state/frames-with-canvas-id canvas-id)]
        (when-let [ufn (frame/param f spec/update-fn-key)]
          (Platform/runLater #(ufn preview w h)))))))

(defn raster-layer-buffer [canvas-id layer-id]
  (when-let [cd (pc/canvas-data! canvas-id)]
    (when-let [l (lc/find-layer layer-id (:layers cd))]
      (when (= :raster (:type l))
        (let [lid (state/selected-layer-id canvas-id)]
          (if (= lid layer-id)
            (when-let [rt (state/canvas-runtime canvas-id)]
              (state/layer-buffer rt))
            (cp/data (:canvas l))))))))

;; ── 路径查询 ──────────────────────────────────────
(defn selected-layer-path [canvas-id]
  (when-let [selected-id (state/selected-layer-id canvas-id)]
    (lc/find-layer-path selected-id (pc/layers-by-id! canvas-id))))

;; ═══════════════════════════════════════════════════════
;; 纯函数统一返回结构说明：
;; 所有返回的 map 键名固定如下，确保解构一致：
;;   :canvas-data  → 新的 CanvasData 实例
;;   :layer        → 受影响图层 map (添加/复制时存在)
;;   :layer-id     → 受影响图层的 ID
;;   :path         → 图层的索引路径
;;   :removed      → 被删除的图层 map
;;   :new-selected-id → 删除后新的选中图层 ID (可能为 nil)
;; ═══════════════════════════════════════════════════════

(defn add-raster-layer-over-selected
  "纯：在选中图层上方添加光栅图层。
   返回 {:canvas-data, :layer, :layer-id, :path}"
  [cd selected-id]
  (let [w   (:width cd)
        h   (:height cd)
        new-layer (rl/make-raster-layer w h)
        layers    (:layers cd)
        path      (if selected-id (lc/find-layer-path selected-id layers) [])
        new-layers (lc/insert-layer path new-layer layers)
        new-cd     (with-layers cd new-layers)]
    {:canvas-data new-cd
     :layer       new-layer
     :layer-id    (:id new-layer)
     :path        path}))

(defn add-raster-layer-over-selected! [canvas-id]
  (let [^CanvasData cd (pc/canvas-data! canvas-id)
        selected-id (state/selected-layer-id canvas-id)
        result (add-raster-layer-over-selected cd selected-id)   ;; 完整纯函数结果
        {:keys [canvas-data layer layer-id path]} result]
    (update-project! canvas-id canvas-data)
    (pr/create-raster! layer-id canvas-id (cp/data (:canvas layer)))
    (state/set-selected-layer-id! canvas-id layer-id)
    (refresh-canvas-frames! canvas-id)
    (hook/run-hook! spec/layer-changed-hook-key canvas-id)
    result))

(defn add-layer-at
  "纯：在路径处插入图层。
   返回 {:canvas-data, :layer, :layer-id, :path}"
  [cd path layer]
  (let [new-layers (lc/insert-layer path layer (:layers cd))
        new-cd     (with-layers cd new-layers)]
    {:canvas-data new-cd
     :layer       layer
     :layer-id    (:id layer)
     :path        path}))

(defn add-layer-at! [canvas-id path layer]
  (let [^CanvasData cd (pc/canvas-data! canvas-id)
        result (add-layer-at cd path layer)
        {:keys [canvas-data layer-id]} result]
    (when canvas-data
      (update-project! canvas-id canvas-data)
      (pr/create-raster! layer-id canvas-id (cp/data (:canvas layer)))
      (refresh-canvas-frames! canvas-id)
      (state/set-selected-layer-id! canvas-id layer-id)
      (hook/run-hook! spec/layer-changed-hook-key canvas-id)
      result)))

;; ── 删除图层 ──────────────────────────────────────

(defn remove-layer-at
  "纯：删除路径处的图层。
   返回 {:canvas-data, :removed, :new-selected-id}"
  [cd selected-id path]
  (let [layers        (:layers cd)
        [final-layers removed] (lc/remove-layer path layers)
        new-cd        (with-layers cd final-layers)
        layer-id      (:id removed)
        ;; 计算新选择的图层
        idx           (last path)
        new-sel       (if (= selected-id layer-id)
                        (let [new-parent (lc/parent-container path final-layers)
                              new-idx    (min idx (dec (count new-parent)))]
                          (when (>= new-idx 0) (:id (nth new-parent new-idx))))
                        selected-id)]
    {:canvas-data new-cd
     :removed     removed
     :layer-id    layer-id
     :new-selected-id new-sel}))

(defn remove-layer-at! [canvas-id path]
  (let [cd (pc/canvas-data! canvas-id)
        selected-id (state/selected-layer-id canvas-id)
        result (remove-layer-at cd selected-id path)
        {:keys [canvas-data _removed layer-id new-selected-id]} result]
    (when canvas-data
      (update-project! canvas-id canvas-data)
      (pr/delete-raster! layer-id)
      (refresh-canvas-frames! canvas-id)
      (when new-selected-id (state/set-selected-layer-id! canvas-id new-selected-id))
      (hook/run-hook! spec/layer-changed-hook-key canvas-id)
      result)))

;; ── 移动图层 ──────────────────────────────────────

(defn move-layer
  "纯：将图层从 old-path 移到 new-path，返回新的 CanvasData。"
  [cd old-path new-path]
  (let [layers       (:layers cd)
        [temp-layers moved] (lc/remove-layer old-path layers)
        same-parent? (= (butlast old-path) (butlast new-path))
        old-idx (last old-path)
        new-idx (last new-path)
        adjusted-new-path (if (and same-parent? (< old-idx new-idx))
                            (conj (butlast new-path) (dec new-idx))
                            new-path)
        final-layers (lc/insert-layer adjusted-new-path moved temp-layers)]
    (with-layers cd final-layers)))

(defn move-layer! [canvas-id old-path new-path]
  (when-let [new-cd (move-layer (pc/canvas-data! canvas-id) old-path new-path)]
    (update-project! canvas-id new-cd)
    (refresh-canvas-frames! canvas-id)
    (hook/run-hook! spec/layer-changed-hook-key canvas-id)
    new-cd))

;; ── 复制图层 ──────────────────────────────────────

(defn duplicate-layer
  "纯：复制图层。
   返回 {:canvas-data, :layer, :layer-id, :path}"
  [cd layer-id]
  (when-let [layer (some #(when (= (:id %) layer-id) %) (:layers cd))]
    (let [canvas      (:canvas layer)
          w           (cp/width canvas)
          h           (cp/height canvas)
          new-canvas  (rl/make-raster-layer w h)
          _           (System/arraycopy (cp/data canvas) 0
                                        (-> new-canvas :canvas cp/data) 0
                                        (int (* w h 4)))
          new-id      (keyword (str (name layer-id) "-copy"))
          new-layer   (-> layer (assoc :id new-id :canvas new-canvas) (dissoc :name))
          layers      (:layers cd)
          idx         (first (keep-indexed #(when (= (:id %2) layer-id) %1) layers))
          new-layers  (if idx
                        (vec (concat (subvec layers 0 (inc idx)) [new-layer] (subvec layers (inc idx))))
                        (conj layers new-layer))]
      {:canvas-data (with-layers cd new-layers)
       :layer       new-layer
       :layer-id    new-id
       :path        (lc/find-layer-path layer-id layers)})))

(defn duplicate-layer! [canvas-id layer-id]
  (let [^CanvasData cd (pc/canvas-data! canvas-id)
        width (:width cd)
        height (:height cd)
        result (duplicate-layer cd layer-id)
        {:keys [canvas-data layer layer-id new-layer-id path]} result]
    (when canvas-data
      (update-project! canvas-id canvas-data)
      (pr/create-raster-empty! new-layer-id canvas-id width height)
      (pm/create-layer-meta! new-layer-id canvas-id)
      (refresh-canvas-frames! canvas-id)
      (state/set-selected-layer-id! canvas-id new-layer-id)
      (hook/run-hook! spec/layer-changed-hook-key canvas-id)
      result)))

;; ── 更新图层 ──────────────────────────────────────

(defn update-layer-at [cd path updater]
  (let [layers      (:layers cd)
        idx         (last path)
        parent-path (butlast path)
        parent      (lc/parent-container path layers)
        old-layer   (nth parent idx)
        new-layer   (updater old-layer)
        new-parent  (assoc-in parent [idx] new-layer)]
    (if (seq parent-path)
      (let [root-updated (assoc-in layers (interleave parent-path (repeat :layers)) new-parent)]
        (with-layers cd root-updated))
      (with-layers cd (assoc layers idx new-layer)))))

(defn update-layer-by-id [cd layer-id updater]
  (when-let [path (lc/find-layer-path layer-id (:layers cd))]
    (update-layer-at cd path updater)))

(defn update-layer-at! [canvas-id path updater]
  (when-let [new-cd (update-layer-at (pc/canvas-data! canvas-id) path updater)]
    (update-project! canvas-id new-cd)
    (refresh-canvas-frames! canvas-id)
    (hook/run-hook! spec/layer-changed-hook-key canvas-id)
    new-cd))

(defn update-layer-by-id! [canvas-id layer-id updater]
  (when-let [new-cd (update-layer-by-id (pc/canvas-data! canvas-id) layer-id updater)]
    (update-project! canvas-id new-cd)
    (refresh-canvas-frames! canvas-id)
    (hook/run-hook! spec/layer-changed-hook-key canvas-id)
    new-cd))

(defn update-selected-layer! [canvas-id updater]
  (when-let [selected-id (state/selected-layer-id canvas-id)]
    (update-layer-by-id! canvas-id selected-id updater)))

;; ── 查询与辅助 ────────────────────────────────────

(defn auto-select-layer! [canvas-id]
  (let [current-id (state/selected-layer-id canvas-id)]
    (if (nil? current-id)
      (let [layers (pc/layers-by-id! canvas-id)]
        (when-let [top (last layers)]
          (let [id (:id top)]
            (state/set-selected-layer-id! canvas-id id)
            id)))
      current-id)))

(defn get-selected-layer [f canvas-id]
  (when-let [id (state/selected-layer-id canvas-id)]
    (some #(when (= (:id %) id) %) (pc/layers-by-id! canvas-id))))

(defn selected-layer-type [f canvas-id]
  (when-let [layer (get-selected-layer f canvas-id)]
    (:type layer)))

(defn selected-raster-layer? [f canvas-id]
  (= :raster (selected-layer-type f canvas-id)))

(defn set-selected-raster-layer-pixels! [f canvas-id pixel-data]
  (when-let [layer (get-selected-layer f canvas-id)]
    (when (= :raster (:type layer))
      (let [canvas (:canvas layer) dest (cp/data canvas)]
        (assert (= (alength dest) (alength pixel-data)) "Pixel data size mismatch")
        (System/arraycopy pixel-data 0 dest 0 (alength dest))))))

(defn copy-selected-raster-layer-pixels! [f canvas-id ^floats dest]
  (if-let [layer (get-selected-layer f canvas-id)]
    (if (= :raster (:type layer))
      (let [src (cp/data (:canvas layer))]
        (assert (= (alength dest) (alength src)) "Destination array size mismatch")
        (System/arraycopy src 0 dest 0 (alength src)) dest)
      (throw (ex-info "Selected layer is not a raster layer" {:type (:type layer)})))
    (throw (ex-info "No layer is currently selected" {}))))
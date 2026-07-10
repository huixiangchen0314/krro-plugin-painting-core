(ns top.kzre.krro.plugin.painting.canvas.layer
  "图层操作：通过不可变方式管理 CanvasData，自动同步到项目原子。
   selected-layer-id 已迁移至 Frame 参数；图层数据直接通过 canvas-id 从项目原子获取。"
  (:require [top.kzre.krro.core.frame :as frame]
            [top.kzre.krro.canvas.core.core :as canv]
            [top.kzre.krro.canvas.raster.core :as rl]
            [top.kzre.krro.canvas.core.canvas.protocol :as cp]
            [top.kzre.krro.core.project :as proj]
            [top.kzre.krro.plugin.painting.canvas.project :as canv-proj]
            [top.kzre.krro.plugin.painting.spec :as spec])
  (:import (java.util Arrays)
           [top.kzre.krro.plugin.painting.canvas.project CanvasData]))

;; ── 内部工具 ──────────────────────────────────────
(defn- with-layers [^CanvasData old-cd new-layers]
  (CanvasData. (.width old-cd) (.height old-cd) (vec new-layers)))

(defn- update-project! [canvas-id ^CanvasData new-cd]
  (swap! proj/project assoc-in [:krro.painting/canvases canvas-id] new-cd))

;; ── 选中图层管理（基于 Frame 参数） ────────────────
(defn get-selected-layer-id [f]
  (frame/param f spec/selected-layer-id-key))

(defn set-selected-layer-id! [f layer-id]
  (frame/set-param! f spec/selected-layer-id-key layer-id))

(defn auto-select-layer!
  "如果没有选中图层，自动选择最顶层图层（若存在）。需要传入 frame 和 canvas-id。"
  [f canvas-id]
  (let [current-id (get-selected-layer-id f)]
    (if (nil? current-id)
      (let [layers (canv-proj/layers-by-id canvas-id)]
        (when-let [top (last layers)]
          (let [id (:id top)]
            (set-selected-layer-id! f id)
            id)))
      current-id)))

(defn get-selected-layer
  "返回当前选中的图层 map。需要传入 frame 和 canvas-id。"
  [f canvas-id]
  (when-let [id (get-selected-layer-id f)]
    (some #(when (= (:id %) id) %) (canv-proj/layers-by-id canvas-id))))


;; ── 图层修改 ──────────────────────────────────────
(defn add-layer!
  [f canvas-id]
  (let [cd (canv-proj/canvas-data canvas-id)
        w (.width ^CanvasData cd)
        h (.height ^CanvasData cd)
        new-layer (rl/make-raster-layer w h)
        new-layers (conj (.layers ^CanvasData cd) new-layer)
        new-cd (with-layers cd new-layers)]
    (update-project! canvas-id new-cd)
    (set-selected-layer-id! f (:id new-layer))
    new-layer))

(defn add-layer-at!
  [f canvas-id index layer]
  (let [cd (canv-proj/canvas-data canvas-id)
        layers (:layers ^CanvasData cd)
        idx (max 0 (min index (count layers)))
        new-layers (vec (concat (subvec layers 0 idx) [layer] (subvec layers idx)))
        new-cd (with-layers cd new-layers)]
    (update-project! canvas-id new-cd)
    (set-selected-layer-id! f (:id layer))
    layer))

(defn remove-layer!
  [f canvas-id layer-id]
  (let [cd (canv-proj/canvas-data canvas-id)
        layers (.layers ^CanvasData cd)
        idx (first (keep-indexed #(when (= (:id %2) layer-id) %1) layers))]
    (when idx
      (let [new-layers (vec (concat (subvec layers 0 idx) (subvec layers (inc idx))))
            new-cd (with-layers cd new-layers)]
        (update-project! canvas-id new-cd)
        (when (= (get-selected-layer-id f) layer-id)
          (let [new-id (cond
                         (< idx (count new-layers)) (:id (nth new-layers idx))
                         (> idx 0) (:id (nth new-layers (dec idx)))
                         :else nil)]
            (set-selected-layer-id! f new-id)))))))

(defn move-layer!
  [f canvas-id layer-id target-index]
  (let [cd (canv-proj/canvas-data canvas-id)
        layers (.layers ^CanvasData cd)
        idx (first (keep-indexed #(when (= (:id %2) layer-id) %1) layers))]
    (when idx
      (let [layer (nth layers idx)
            without (vec (concat (subvec layers 0 idx) (subvec layers (inc idx))))
            insert-pos (max 0 (min target-index (count without)))
            new-layers (vec (concat (subvec without 0 insert-pos) [layer] (subvec without insert-pos)))
            new-cd (with-layers cd new-layers)]
        (update-project! canvas-id new-cd)
        (set-selected-layer-id! f layer-id)))))

(defn update-layer!
  [f canvas-id layer-id updater]
  (let [cd (canv-proj/canvas-data canvas-id)
        layers (.layers ^CanvasData cd)
        idx (first (keep-indexed #(when (= (:id %2) layer-id) %1) layers))]
    (when idx
      (let [new-layer (updater (nth layers idx))
            new-layers (assoc layers idx new-layer)
            new-cd (with-layers cd new-layers)]
        (update-project! canvas-id new-cd)))))

(defn duplicate-layer!
  [f canvas-id layer-id]
  (when-let [layer (some #(when (= (:id %) layer-id) %) (canv-proj/layers-by-id canvas-id))]
    (let [canvas (:canvas layer)
          w (cp/width canvas)
          h (cp/height canvas)
          new-canvas (rl/make-raster-layer w h)
          new-canvas-data (-> new-canvas :canvas cp/data)
          _ (System/arraycopy (cp/data canvas) 0 new-canvas-data 0 (alength new-canvas-data))
          new-id (keyword (str (name layer-id) "-copy"))
          new-layer (-> layer (assoc :id new-id :canvas new-canvas) (dissoc :name))
          cd (canv-proj/canvas-data canvas-id)
          layers (.layers ^CanvasData cd)
          idx (first (keep-indexed #(when (= (:id %2) layer-id) %1) layers))
          new-layers (if idx
                       (vec (concat (subvec layers 0 (inc idx)) [new-layer] (subvec layers (inc idx))))
                       (conj layers new-layer))
          new-cd (with-layers cd new-layers)]
      (update-project! canvas-id new-cd)
      (set-selected-layer-id! f new-id)
      new-layer)))

(defn selected-layer-type
  [f canvas-id]
  (when-let [layer (get-selected-layer f canvas-id)]
    (:type layer)))

(defn selected-raster-layer?
  [f canvas-id]
  (= :raster (selected-layer-type f canvas-id)))

(defn set-selected-raster-layer-pixels!
  [f canvas-id pixel-data]
  (when-let [layer (get-selected-layer f canvas-id)]
    (when (= :raster (:type layer))
      (let [canvas (:canvas layer)
            dest (cp/data canvas)]
        (assert (= (alength dest) (alength pixel-data)) "Pixel data size mismatch")
        (System/arraycopy pixel-data 0 dest 0 (alength dest))))))

(defn copy-selected-raster-layer-pixels!
  [f canvas-id ^floats dest]
  (if-let [layer (get-selected-layer f canvas-id)]
    (if (= :raster (:type layer))
      (let [src (cp/data (:canvas layer))]
        (assert (= (alength dest) (alength src)) "Destination array size mismatch")
        (System/arraycopy src 0 dest 0 (alength src))
        dest)
      (throw (ex-info "Selected layer is not a raster layer" {:type (:type layer)})))
    (throw (ex-info "No layer is currently selected" {}))))

(defn render-canvas-by-id!
  "渲染当前画布所有图层到目标数组。"
  [canvas-id ^floats dest]
  (when-let [cd (canv-proj/canvas-data canvas-id)]
    (let [layers (:layers ^CanvasData cd)
          w (.width ^CanvasData cd)
          h (.height ^CanvasData cd)]
      (Arrays/fill dest (float 0.0))
      (canv/render-layers! layers dest w h))))



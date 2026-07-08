(ns top.kzre.krro.plugin.painting.canvas.layer
  "图层操作：通过不可变方式管理 CanvasData，自动同步到项目原子。"
  (:require [top.kzre.krro.canvas.core.core :as canv]
            [top.kzre.krro.canvas.raster.core :as rl]
            [top.kzre.krro.canvas.core.canvas.protocol :as cp]
            [top.kzre.krro.core.project :as proj]
            [top.kzre.krro.plugin.painting.canvas.state :as state])
  (:import (java.util Arrays)
           [top.kzre.krro.plugin.painting.canvas.state CanvasRuntime]
           [top.kzre.krro.plugin.painting.canvas.project CanvasData]))

;; ── 内部工具 ──────────────────────────────────────
(defn- with-layers
  "构造新的 CanvasData 实例，用于替换旧实例。"
  [^CanvasData old-cd new-layers]
  (CanvasData. (.width old-cd) (.height old-cd) (vec new-layers)))

(defn- update-project!
  "将新的 CanvasData 写入项目原子，并同步到运行时引用。"
  [canvas-id ^CanvasData new-cd runtime]
  (swap! proj/project assoc-in [:krro.painting/canvases canvas-id] new-cd)
  (state/set-canvas-data! runtime new-cd))

;; ── 选中图层管理 ──────────────────────────────────
(defn get-selected-layer-id [^CanvasRuntime rt]
  @(:selected-layer-id rt))

(defn set-selected-layer-id! [^CanvasRuntime rt layer-id]
  (reset! (:selected-layer-id rt) layer-id))



;; ── 图层访问 ──────────────────────────────────────
(defn get-layers
  "返回当前所有图层向量（只读）。"
  [^CanvasRuntime rt]
  (let [^CanvasData cd (state/get-canvas-data rt)]
    (.layers cd)))

(defn auto-select-layer!
  "如果没有选中图层，自动选择最顶层图层（若存在）。返回选中的图层 id 或 nil。"
  [^CanvasRuntime rt]
  (let [current-id (get-selected-layer-id rt)]
    (if (nil? current-id)
      (let [layers (get-layers rt)]
        (when-let [top (last layers)]
          (let [id (:id top)]
            (set-selected-layer-id! rt id)
            id)))
      current-id)))

(defn get-selected-layer [^CanvasRuntime rt]
  (when-let [id (get-selected-layer-id rt)]
    (some #(when (= (:id %) id) %) (get-layers rt))))

;; ── 图层修改（不可变，返回新 CanvasData 并同步）─
(defn add-layer!
  "在图层栈顶部添加一个光栅图层，自动选中。
   需要 canvas-id 以确定在项目中的存储路径。"
  [^CanvasRuntime rt canvas-id]
  (let [cd (state/get-canvas-data rt)
        w (.width cd)
        h (.height cd)
        new-layer (rl/make-raster-layer w h)
        new-layers (conj (.layers cd) new-layer)
        new-cd (with-layers cd new-layers)]
    (update-project! canvas-id new-cd rt)
    (set-selected-layer-id! rt (:id new-layer))
    new-layer))

(defn add-layer-at!
  [^CanvasRuntime rt canvas-id index layer]
  (let [cd (state/get-canvas-data rt)
        layers (.layers cd)
        idx (max 0 (min index (count layers)))
        new-layers (vec (concat (subvec layers 0 idx) [layer] (subvec layers idx)))
        new-cd (with-layers cd new-layers)]
    (update-project! canvas-id new-cd rt)
    (set-selected-layer-id! rt (:id layer))
    layer))

(defn remove-layer!
  [^CanvasRuntime rt canvas-id layer-id]
  (let [cd (state/get-canvas-data rt)
        layers (.layers cd)
        idx (first (keep-indexed #(when (= (:id %2) layer-id) %1) layers))]
    (when idx
      (let [new-layers (vec (concat (subvec layers 0 idx) (subvec layers (inc idx))))
            new-cd (with-layers cd new-layers)]
        (update-project! canvas-id new-cd rt)
        (when (= (get-selected-layer-id rt) layer-id)
          (let [new-id (cond
                         (< idx (count new-layers)) (:id (nth new-layers idx))
                         (> idx 0) (:id (nth new-layers (dec idx)))
                         :else nil)]
            (set-selected-layer-id! rt new-id)))))))

(defn move-layer!
  [^CanvasRuntime rt canvas-id layer-id target-index]
  (let [cd (state/get-canvas-data rt)
        layers (.layers cd)
        idx (first (keep-indexed #(when (= (:id %2) layer-id) %1) layers))]
    (when idx
      (let [layer (nth layers idx)
            without (vec (concat (subvec layers 0 idx) (subvec layers (inc idx))))
            insert-pos (max 0 (min target-index (count without)))
            new-layers (vec (concat (subvec without 0 insert-pos) [layer] (subvec without insert-pos)))
            new-cd (with-layers cd new-layers)]
        (update-project! canvas-id new-cd rt)
        (set-selected-layer-id! rt layer-id)))))

(defn update-layer!
  [^CanvasRuntime rt canvas-id layer-id f]
  (let [cd (state/get-canvas-data rt)
        layers (.layers cd)
        idx (first (keep-indexed #(when (= (:id %2) layer-id) %1) layers))]
    (when idx
      (let [new-layer (f (nth layers idx))
            new-layers (assoc layers idx new-layer)
            new-cd (with-layers cd new-layers)]
        (update-project! canvas-id new-cd rt)))))

(defn duplicate-layer!
  [^CanvasRuntime rt canvas-id layer-id]
  (when-let [layer (some #(when (= (:id %) layer-id) %) (get-layers rt))]
    (let [canvas (:canvas layer)
          w (cp/width canvas)
          h (cp/height canvas)
          ;; 拷贝像素数据
          new-canvas (rl/make-raster-layer w h)
          new-canvas-data (-> new-canvas :canvas cp/data)
          _ (System/arraycopy (cp/data canvas) 0 new-canvas-data 0 (alength new-canvas-data))
          new-id (keyword (str (name layer-id) "-copy"))
          new-layer (-> layer
                        (assoc :id new-id :canvas new-canvas)
                        (dissoc :name))
          cd (state/get-canvas-data rt)
          layers (.layers cd)
          idx (first (keep-indexed #(when (= (:id %2) layer-id) %1) layers))
          new-layers (if idx
                       (vec (concat (subvec layers 0 (inc idx)) [new-layer] (subvec layers (inc idx))))
                       (conj layers new-layer))
          new-cd (new-canvas-data cd new-layers)]
      (update-project! canvas-id new-cd rt)
      (set-selected-layer-id! rt new-id)
      new-layer)))

(defn selected-layer-type
  "返回当前选中图层的 :type 关键字，若无选中图层返回 nil。"
  [^CanvasRuntime rt]
  (when-let [layer (get-selected-layer rt)]
    (:type layer)))

(defn selected-raster-layer?
  "判断当前选中图层是否为光栅图层 (:raster)。"
  [^CanvasRuntime rt]
  (= :raster (selected-layer-type rt)))

(defn set-selected-raster-layer-pixels!
  "将给定的 float-array 像素数据直接复制到当前选中图层的 canvas 中。
   要求图层类型为 :raster，且数据长度必须与图层画布匹配。
   此操作会直接修改项目原子中的数据（原地更新），与渲染循环中的像素提交方式一致。
   因此无需额外同步，但调用者应保证数据尺寸正确。"
  [^CanvasRuntime rt pixel-data]
  (when-let [layer (get-selected-layer rt)]
    (when (= :raster (:type layer))
      (let [canvas (:canvas layer)
            dest   (cp/data canvas)]
        (assert (= (alength dest) (alength pixel-data))
                "Pixel data size mismatch")
        (System/arraycopy pixel-data 0 dest 0 (alength dest)))
      )))

(defn copy-selected-raster-layer-pixels!
  "将当前选中光栅图层的像素数据复制到目标数组 dest 中。
   要求当前选中的图层必须是 :raster 类型，且 dest 长度必须与图层像素数据长度一致。"
  [^CanvasRuntime rt ^floats dest]
  (if-let [layer (get-selected-layer rt)]
    (if (= :raster (:type layer))
      (let [src (cp/data (:canvas layer))]
        (assert (= (alength dest) (alength src))
                "Destination array size mismatch with layer pixel data")
        (System/arraycopy src 0 dest 0 (alength src))
        dest)
      (throw (ex-info "Selected layer is not a raster layer" {:type (:type layer)})))
    (throw (ex-info "No layer is currently selected" {}))))

(defn render-canvas!
  "渲染当前画布,简化成拷贝当前图层"
  [^CanvasRuntime rt ^floats dest]
  (let [cd (state/get-canvas-data rt)
        layers (.layers cd)
        w (.width cd)
        h (.-height cd)]
    (Arrays/fill dest (float 0.0))
    (canv/render-layers! layers dest w h)))
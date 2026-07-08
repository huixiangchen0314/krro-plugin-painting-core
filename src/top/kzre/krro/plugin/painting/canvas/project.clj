(ns top.kzre.krro.plugin.painting.canvas.project
  "项目数据集成."
  (:require [top.kzre.krro.core.project :as proj]
            [top.kzre.krro.canvas.raster.core :as rl]
            [top.kzre.krro.core.resource :as res]))

;; ── 专用画布数据容器 ──────────────────────────────
(deftype CanvasData [width height layers])

(def canvas-codec-plugin-def
  {:type    :krro.plugin/resource-codec
   :id      :krro.painting/canvas-codec
   :resource :krro.painting/canvas-data
   :encoder (fn [^CanvasData c]
              {:krro/type :krro.painting/canvas-data
               :width (.width c)
               :height (.height c)
               :layers (.layers c)})
   :decoder (fn [m]
              (CanvasData. (:width m) (:height m)
                           (res/realize (:layers m)) ))})

(defn polyfill-canvas-data!
  "确保项目原子中存在活跃的 CanvasData，并返回该实例。
   若画布已存在，以项目中的实际尺寸为准（忽略传入的 width/height）。
   若不存在，则用传入的 width/height 创建新画布。
   canvas-id  : 画布标识
   width, height : 画布尺寸（仅在首次创建时作为默认值）"
  [canvas-id width height]
  (let [canvas (proj/get-in-project! [:krro.painting/canvases canvas-id] ::not-found)]
    (if (instance? CanvasData canvas)
      canvas  ;; 直接返回项目中的实例
      ;; 创建新画布，并注册到项目
      (let [default-layer (rl/make-raster-layer width height)
            new-canvas (CanvasData. width height [default-layer])]
        (swap! proj/project assoc-in [:krro.painting/canvases canvas-id] new-canvas)
        new-canvas))))


(deftype LayerMeta [locked? alpha-locked? expanded?])


;; ── 图层元数据编解码器 ──────────────────────────
(def layer-meta-codec-plugin-def
  {:type     :krro.plugin/resource-codec
   :id       :krro.painting/layer-meta-codec
   :resource :krro.painting/layer-meta
   :encoder  (fn [^LayerMeta m]
               {:krro/type      :krro.painting/layer-meta
                :locked?        (.locked? m)
                :alpha-locked?  (.alpha-locked? m)
                :expanded?      (.expanded? m)})
   :decoder  (fn [m]
               (LayerMeta. (boolean (:locked? m))
                           (boolean (:alpha-locked? m))
                           (boolean (:expanded? m))))})

;; ── 图层元数据（侧表，存储于项目原子）─────────
(defn polyfill-layer-meta!
  "确保项目原子中存在指定图层的 LayerMeta 实例，返回该实例。
   若不存在，则创建一个默认（所有属性为 false）的 LayerMeta 并存储。"
  ^LayerMeta
  [canvas-id layer-id]
  (let [path [:krro.painting/layer-meta canvas-id layer-id]
        existing (get-in @proj/project path)]
    (if (instance? LayerMeta existing)
      existing
      (let [new-meta (LayerMeta. false false false)]
        (swap! proj/project assoc-in path new-meta)
        new-meta))))

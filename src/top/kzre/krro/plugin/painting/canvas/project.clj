(ns top.kzre.krro.plugin.painting.canvas.project
  "项目数据集成."
  (:require [top.kzre.krro.core.project :as proj]
            [top.kzre.krro.core.resource :as res]))

;; ── 专用画布数据容器 ──────────────────────────────
(deftype CanvasData [width height data])

(def canvas-codec-plugin-def
  {:type    :krro.plugin/resource-codec
   :id      :krro.painting/canvas-codec
   :resource :krro.painting/canvas-data
   :encoder (fn [^CanvasData c]
              {:krro/type :krro.painting/canvas-data
               :width (.width c)
               :height (.height c)
               :data (.data c)})
   :decoder (fn [m]
              (CanvasData. (:width m) (:height m) (res/realize (:data m))))})

(defn polyfill-canvas-data
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
      (let [new-data (float-array (* width height 4) 0.0)
            new-canvas (CanvasData. width height new-data)]
        (swap! proj/project assoc-in [:krro.painting/canvases canvas-id] new-canvas)
        new-canvas))))
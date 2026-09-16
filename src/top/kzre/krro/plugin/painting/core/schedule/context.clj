(ns top.kzre.krro.plugin.painting.core.schedule.context
  "上下文以及预处理"
  (:require
   [top.kzre.krro.canvas.core.layer.util :as layer-util]
   [top.kzre.krro.core.util.computing-graph :as cg]
   [top.kzre.krro.plugin.painting.core.schedule.util :as util]
   [top.kzre.krro.plugin.painting.core.viewport :as vp]
   [top.kzre.krro.core.util.promise :as promise])
  (:import
   (top.kzre.krro.canvas.core.layer LayerUtils)
   (top.kzre.krro.util.math KMath)))

(defonce ^:private context-key* ::context)

(defn context-key [] context-key*)

(defrecord ContextNode [ctx]
  cg/INode
  (node-id [_] (context-key))
  (dependencies [_] #{})
  (compute [_ _] (promise/resolved ctx)))

(defn make-context-node [ctx]
  (->ContextNode ctx))

(defn diff-info [old-ctx new-ctx]
  (if old-ctx
    {:same-viewport? (= (:viewport old-ctx) (:viewport new-ctx))}
    {:same-viewport? false}))

(defn assoc-view-matrix [ctx old-ctx same-viewport?]
  (if same-viewport?
    (assoc ctx :view-matrix (:view-matrix old-ctx))
    (assoc ctx :view-matrix (vp/viewport->mat2d (:viewport ctx)))))

(defn- normalize-dirty-pairs
  "把 dirty-tiles / dirty-transform 规范化为配对序列。
   支持：
     - 单个值：dirty-tiles 是集合，dirty-transform 是矩阵
     - 向量：dirty-tiles 和 dirty-transform 一一配对
     - 混合：dirty-tiles 是向量，dirty-transform 是单个（共享）
   返回 [[tiles transform] ...]"
  [dirty-tiles dirty-transform]
  (cond
    ;; 都为空 → 空配对
    (or (nil? dirty-tiles) (nil? dirty-transform))
    []

    ;; dirty-tiles 是向量（多组）
    (and (vector? dirty-tiles)
         (vector? dirty-transform)
         (= (count dirty-tiles) (count dirty-transform)))
    (mapv vector dirty-tiles dirty-transform)

    ;; dirty-tiles 是向量，transform 共享
    (vector? dirty-tiles)
    (mapv (fn [t] [t dirty-transform]) dirty-tiles)

    ;; 单个
    :else
    [[dirty-tiles dirty-transform]]))

(defn assoc-view-dirty-tiles
  "计算视口脏瓦片。

   dirty-tiles / dirty-transform 支持：
     - 单组：直接传值
     - 多组：dirty-tiles 和 dirty-transform 是等长向量
       或 dirty-tiles 是向量、dirty-transform 是单个（共享）
     - nil：表示全脏——直接返回全视口瓦片——不再逐组计算

   多组结果合并——同一瓦片出现在多组——只保留一份。"
  [ctx _old-ctx]
  (let [{:keys [tile-size
                view-matrix viewport-w viewport-h
                dirty-tiles dirty-transform]} ctx]

    ;; ── 特判：任一为 nil → 全视口脏
    (if (or (nil? dirty-tiles) (nil? dirty-transform))
      (assoc ctx :view-dirty-tiles
                 (set (LayerUtils/canvasTiles tile-size viewport-w viewport-h)))

      ;; ── 正常路径：逐组计算
      (let [pairs (normalize-dirty-pairs dirty-tiles dirty-transform)

            all-view-tiles
            (reduce
              (fn [acc [tiles transform]]
                (let [transform-to-view
                      (when (and view-matrix tiles transform)
                        (KMath/mat2dMul view-matrix transform))
                      view-tiles
                      (when transform-to-view
                        (util/dirty-region tiles transform-to-view
                                           viewport-w viewport-h tile-size))]
                  (if view-tiles
                    (into acc view-tiles)
                    acc)))
              #{}
              pairs)

            clipped
            (LayerUtils/clipTiles all-view-tiles tile-size
                                  viewport-w viewport-h)]
        (assoc ctx :view-dirty-tiles clipped)))))

(defn assoc-image-dirty-tiles [ctx _old-ctx ]
  (let [{:keys [view-dirty-tiles view-matrix tile-size
                image-width image-height]} ctx

        ;; 裁剪到图像范围（若提供）
        image-clipped-dirty-tiles
        (if (and image-width image-height view-matrix)
          (let [pmin (layer-util/transform-point view-matrix 0 0)
                pmax (layer-util/transform-point view-matrix image-width image-height)]
            (LayerUtils/clipTilesAABB view-dirty-tiles tile-size
                                      (:x pmin) (:y pmin)
                                      (:x pmax) (:y pmax)))
          view-dirty-tiles)]
    (assoc ctx :image-dirty-tiles image-clipped-dirty-tiles)))

(defn diff
  [old-ctx new-ctx {:keys [same-viewport?]}]
  (-> new-ctx
      (assoc-view-matrix old-ctx same-viewport?)
      (assoc-view-dirty-tiles old-ctx)
      (assoc-image-dirty-tiles old-ctx)))


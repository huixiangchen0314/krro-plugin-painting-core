(ns top.kzre.krro.plugin.painting.core.schedule.context
  "上下文以及预处理"
  (:require
   [top.kzre.krro.canvas.core.layer.util :as layer-util]
   [top.kzre.krro.core.util.computing-graph :as cg]
   [top.kzre.krro.plugin.painting.core.schedule.util :as util]
   [top.kzre.krro.plugin.painting.core.changes.viewport]
   [top.kzre.krro.plugin.painting.core.viewport :as vp]
   [top.kzre.krro.core.util.promise :as promise])
  (:import
    (top.kzre.krro.canvas.core.layer LayerUtils)
    (top.kzre.krro.plugin.painting.core.changes.viewport ViewportRefreshed)
    (top.kzre.krro.util.math KMath)))


(defrecord ContextNode [ctx]
  cg/INode
  (node-id [_] :context)
  (dependencies [_] #{})
  (compute [_ _] (promise/resolved ctx)))

(defn make-context-node [ctx]
  (->ContextNode ctx))

(defn diff-info [old-ctx new-ctx]
  (merge
    (if old-ctx
      {:same-viewport? (= (:viewport old-ctx) (:viewport new-ctx))
       :same-subpixel? (= (:subpixel? old-ctx) (:subpixel? new-ctx))}
      {:same-viewport? false
       :same-subpixel? false})
    {:has-change?    (boolean (seq (:changes new-ctx)))}))

(defn assoc-view-matrix [ctx old-ctx same-viewport?]
  (if same-viewport?
    (assoc ctx :view-matrix (:view-matrix old-ctx))
    (assoc ctx :view-matrix (vp/viewport->mat2d (:viewport ctx)))))


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
                change]} ctx]

    (if  (util/contains-change? change ViewportRefreshed)
      (assoc ctx :view-dirty-tiles
                 (set (LayerUtils/canvasTiles tile-size viewport-w viewport-h)))

      ;; ── 正常路径：逐组计算
      (let [pairs (util/change-dirty-pairs change)

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

(defn- transform-points-aabb
  "对四个角点变换后取 AABB。"
  [view-matrix image-width image-height]
  (let [corners [[0 0]
                 [image-width 0]
                 [0 image-height]
                 [image-width image-height]]
        pts (map (fn [[x y]] (layer-util/transform-point view-matrix x y))
                 corners)]
    {:min-x (reduce min (map :x pts))
     :min-y (reduce min (map :y pts))
     :max-x (reduce max (map :x pts))
     :max-y (reduce max (map :y pts))}))

(defn assoc-image-dirty-tiles [ctx _old-ctx]
  (let [{:keys [view-dirty-tiles view-matrix tile-size
                image-width image-height]} ctx]
    (if (and image-width image-height view-matrix)
      (let [image-aabb (transform-points-aabb view-matrix image-width image-height)
            image-clipped-dirty-tiles
            (LayerUtils/clipTilesAABB view-dirty-tiles tile-size
                                      (:min-x image-aabb) (:min-y image-aabb)
                                      (:max-x image-aabb) (:max-y image-aabb))]
        (assoc ctx
          :image-aabb image-aabb
          :image-dirty-tiles image-clipped-dirty-tiles))
      (assoc ctx
        :image-aabb nil
        :image-dirty-tiles view-dirty-tiles))))

(defn assoc-mem-budget
  [ctx]
  (assoc ctx
    :max-mem (* 256 1024 1024)       ;; 512 MB
    :max-vmem (* 1024 1024 1024)      ;; 1 GB
    ))

(defn normalize-changes
  "合并 changes 序列 成 单个 composite"
  [new-ctx]
  (let [changes (:changes new-ctx)]
    (-> new-ctx
        (dissoc :changes)
        (assoc :change (util/normalize-changes changes)))))


(defn ensure-default [ctx]
  (cond-> ctx
          (some? (:subpixel? ctx))
          (assoc :subpixel? false)))

(defn diff
  [old-ctx new-ctx {:keys [same-viewport?]}]
  (-> new-ctx
      (normalize-changes)
      (assoc-view-matrix old-ctx same-viewport?)
      (assoc-view-dirty-tiles old-ctx)
      (assoc-image-dirty-tiles old-ctx)
      (assoc-mem-budget)
      (ensure-default)))


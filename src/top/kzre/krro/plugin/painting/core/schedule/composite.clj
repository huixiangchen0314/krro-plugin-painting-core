(ns top.kzre.krro.plugin.painting.core.schedule.composite
  "通用合成节点"
  (:require
   [top.kzre.krro.canvas.core.layer.render.core :as render]
   [top.kzre.krro.core.util.computing-graph :as cg]
   [top.kzre.krro.core.util.promise :as promise]
   [top.kzre.krro.plugin.painting.core.schedule.cache :as cache]
   [top.kzre.krro.plugin.painting.core.schedule.context :as context]
   [top.kzre.krro.plugin.painting.core.schedule.util :as util]
   [top.kzre.krro.plugin.painting.core.schedule.layer-impl :as layer-impl]
   [clojure.set :as set]
   [top.kzre.krro.plugin.painting.core.schedule.protocol :as proto]
   [taoensso.timbre :as log])
  (:import
    [java.lang AutoCloseable]
    (top.kzre.krro.canvas.core.layer LayerUtils)
    (top.kzre.krro.plugin.painting.core.schedule.cache RasterCache)
    (top.kzre.krro.util.math KMath)
    (top.kzre.krro.util.tile CanvasUtils TiledCanvas)))

;; ═══════════════════════════════════════════════
;; 状态
;; ═══════════════════════════════════════════════

(defrecord CompositeState
  [^RasterCache cache
   ^boolean caching])

(defn make-state []
  (->CompositeState nil false))

;; ═══════════════════════════════════════════════
;; 内部辅助
;; ═══════════════════════════════════════════════

(defn- composite-viewport-id
  "CompositeNode 的稳定 viewport id——同一节点实例永远返回同一个 id。"
  [node]
  (keyword (str "composite-" (hash (cg/node-id node)))))


(defn- update-cache
  "更新缓存状态：
     - 清理旧缓存（吞异常——不遮蔽调用方）
     - caching 为 true 时存入新缓存的副本
     - caching 为 false 时清空缓存字段"
  [state composited {:keys [view-matrix]}]
  (when-let [old-canvas (:cache state)]
    (try (.close old-canvas) (catch Throwable _ nil)))
  (if (:caching state)
    (assoc state :cache (cache/->RasterCache (.copy composited) view-matrix))
    (dissoc state :cache)))


(defn- composite-layers
  "直接调用底层 render-layers!——不做调度合并——合并由上层调度器决定。
   返回 Promise 在合成完成时以 composited 画布完成。"
  [node layers
   {:keys [view-matrix viewport-h viewport-w
           tile-size image-width image-height image-dirty-tiles]
    :as ctx}]
  (promise/plet
    [composited (promise/spawn
                  (fn []
                    (render/render
                      (mapv
                        (fn [l]
                          {:pre [(some? l)]}
                          (util/->raster-layer l) )
                        layers)
                      {:tile-size        tile-size
                       :view-width       viewport-w
                       :view-height      viewport-h
                       :view-matrix      view-matrix
                       :dirty-tiles      image-dirty-tiles
                       :image-width      image-width
                       :image-height     image-height})))]
    (swap! (:state-atom node) update-cache composited ctx)
    (layer-impl/make-layer
      (composite-viewport-id node)
      composited)))

(defn- composite-layers-cached
  "把各个图层变换到旧的视口空间，合成。
  结果携带累积变换
  "
  [node layers
   {:keys [view-matrix viewport-h viewport-w
           tile-size image-width image-height image-dirty-tiles]}]
  (let [{:keys [cache]} @(:state-atom node)
        old-view-matrix (:view-matrix cache)
        ^TiledCanvas old-canvas (:canvas cache)
        to-old-view-matrix (KMath/mat2dMul old-view-matrix (KMath/mat2dInv view-matrix))
        ;; 结果图层携带这个变换到新视口空间的变换
        to-new-view-matrix (KMath/mat2dMul view-matrix (KMath/mat2dInv old-view-matrix))
        old-image-dirty-tiles (LayerUtils/transformTiles image-dirty-tiles tile-size to-old-view-matrix)
        cached-tiles (.getTiles old-canvas)]
    (promise/plet
      [composited
       (promise/spawn
         (fn []
           ;;在旧空间合成
           (render/render
             (mapv (fn [l]
                     (let [layer (util/->raster-layer l)]
                       (update layer :transform
                               (fn [matrix] (KMath/mat2dMul to-old-view-matrix matrix))))) layers)
             {:tile-size        tile-size
              ;; 裁剪掉已经缓存的瓦片
              :dirty-tiles      (set/difference old-image-dirty-tiles cached-tiles)
              :view-width       viewport-w
              :view-height      viewport-h
              :image-width      image-width
              :image-height     image-height})))]
      ;; 更新缓存缓存
      (.mergeCanvas old-canvas composited)
      (.close composited)
      (layer-impl/make-layer
        (composite-viewport-id node)
        ;; 计算结果所有权不属于我们
        (.copy old-canvas)
        to-new-view-matrix))))

;; ═══════════════════════════════════════════════
;; CompositeNode
;; ═══════════════════════════════════════════════

(defrecord CompositeNode [layer-ids state-atom]
  proto/ICachingNode
  (set-caching! [_ b]
    (log/debug "set-caching!")
    (swap! state-atom assoc :caching b))
  (caching? [_] (:caching @state-atom))
  (cached? [_] (some? (:cache @state-atom)))
  (invalidate-cache! [_]
    (log/debug "invalidate-cache!")
    (swap! state-atom
           (fn [state]
             (when-let [c (:cache state)]
               (try (.close ^AutoCloseable c) (catch Throwable _ nil)))
             (dissoc state :cache))))
  (migrate [this other change]
    (log/debug "migrate change"))
  (cache-value [_] 1)
  (mem-cost [_] 1024)
  (vmem-cost [_] 0)
  cg/INode
  (node-id [_] (into [:context] layer-ids))
  (dependencies [this] (cg/node-id this))
  (compute [this [ctx & layers]]
    (if (and (proto/caching? this)
             (proto/cached? this))
      ;; 命中缓存,在旧视口空间合成
      (composite-layers-cached this layers ctx)
      ;; ── 未命中——合成 → 更新缓存 ──
      (composite-layers this layers ctx))))

(defn make-composite-node [layer-nodes]
  (->CompositeNode  (mapv #(cg/node-id %) layer-nodes)
                    (atom (make-state))))
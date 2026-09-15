(ns top.kzre.krro.plugin.painting.core.schedule.composite
  "通用合成节点"
  (:require
    [top.kzre.krro.canvas.core.core :as canv]
    [top.kzre.krro.core.util.promise :as promise]
    [top.kzre.krro.plugin.painting.core.schedule.protocol :as proto]
    [top.kzre.krro.plugin.painting.core.schedule.util :as schedule.util]
    [top.kzre.krro.plugin.painting.core.schedule.viewport-layer :as viewport-layer])
  (:import
    (java.util Set)
    (top.kzre.krro.util.tile TiledCanvas)))

;; ═══════════════════════════════════════════════
;; 状态
;; ═══════════════════════════════════════════════

(defrecord CompositeState
  [^TiledCanvas composited-canvas
   ^boolean caching])

(defn make-state []
  (->CompositeState nil false))

;; ═══════════════════════════════════════════════
;; 内部辅助
;; ═══════════════════════════════════════════════

(defn- composite-viewport-id
  "CompositeNode 的稳定 viewport id——同一节点实例永远返回同一个 id。"
  [node]
  (keyword (str "composite-" (hash (proto/node-key node)))))

(defn- render-composite!
  "把 layers 合成到 (:canvas ctx)——原地更新——返回 Promise<TiledCanvas>。

   直接调用底层 render-layers!——不做调度合并——合并由上层调度器决定。
   返回 Promise 在合成完成时以 composited 画布完成。"
  [node ctx layers]
  (let [^TiledCanvas composited (:canvas ctx)
        canvas-data            (:canvas-data ctx)
        {:keys [view-matrix viewport-h viewport-w viewport-dirty-tiles
                tile-size]} ctx
        composed (mapv #(schedule.util/->raster-layer %) layers)]

    (-> (canv/render-layers!
          composed
          :transform-composed? true
          :tile-size        tile-size
          :view-width       viewport-w
          :view-height      viewport-h
          :view-matrix      view-matrix
          :view-dirty-tiles viewport-dirty-tiles
          :image-width      (:width canvas-data)
          :image-height     (:height canvas-data))
        (promise/fmap
          (fn [{:keys [^TiledCanvas canvas dirty-tiles]}]
            ;; 2. 合并差分到目标
            (.deleteTiles composited ^Set dirty-tiles)
            (.mergeCanvas composited canvas)
            ;; 3. 释放差分画布
            (.clear canvas)
            composited)))))

(defn- update-cache!
  "更新缓存状态：
     - 清理旧缓存（吞异常——不遮蔽调用方）
     - caching 为 true 时存入新缓存的副本
     - caching 为 false 时清空缓存字段"
  [state-atom composited]
  (swap! state-atom
         (fn [state]
           (when-let [old-canvas (:composited-canvas state)]
             (try (.clear old-canvas) (catch Throwable _ nil)))
           (if (:caching state)
             (assoc state :composited-canvas (.copy composited))
             (dissoc state :composited-canvas)))))

(defn- clear-cache!
  "清空缓存——清理 canvas 并移除字段。吞异常。"
  [state-atom]
  (swap! state-atom
         (fn [state]
           (when-let [c (:composited-canvas state)]
             (try (.clear c) (catch Throwable _ nil)))
           (dissoc state :composited-canvas))))

;; ═══════════════════════════════════════════════
;; CompositeNode
;; ═══════════════════════════════════════════════


(defrecord CompositeNode [ layer-nodes state-atom]
  proto/IRenderNode

  (node-key [_]
    (mapv #(proto/node-key %) layer-nodes))

  (inputs [_]
    layer-nodes)

  (set-caching! [_ b]
    (swap! state-atom assoc :caching (boolean b)))

  (caching? [_]
    (boolean (:caching @state-atom)))

  (cached? [_]
    (some? (:composited-canvas @state-atom)))

  (invalidate-cache! [_]
    (clear-cache! state-atom))

  (request! [this ctx]
    (if-let [cached-canvas (:composited-canvas @state-atom)]
      ;; ── 命中缓存——直接返回 ──────────────────
      (promise/resolved
        (viewport-layer/make-viewport-layer
          (composite-viewport-id this)
          cached-canvas))

      ;; ── 未命中——并行请求 inputs → 合成 → 更新缓存 ──
      (promise/plet
        [layers     (promise/all (mapv #(proto/request! % ctx) layer-nodes))
         composited (render-composite! this ctx layers)]
        ;; body——最后返回 ViewportLayer
        (update-cache! state-atom composited)
        (viewport-layer/make-viewport-layer
          (composite-viewport-id this)
          composited
          )))))

(defn make-composite-node [layer-nodes]
  (->CompositeNode  layer-nodes (atom (make-state))))
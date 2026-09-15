(ns top.kzre.krro.plugin.painting.core.schedule.composite
  "通用合成节点"
  (:require
    [top.kzre.krro.canvas.core.layer.render.core :as render]
    [top.kzre.krro.core.util.computing-graph :as cg]
    [top.kzre.krro.core.util.promise :as promise]
    [top.kzre.krro.plugin.painting.core.schedule.context :as context]
    [top.kzre.krro.plugin.painting.core.schedule.protocol :as proto]
    [top.kzre.krro.plugin.painting.core.schedule.viewport-layer :as viewport-layer]
    [top.kzre.krro.plugin.painting.core.schedule.util :as util])
  (:import
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
  [layers
   {:keys [view-matrix viewport-h viewport-w
           tile-size image-width image-height image-dirty-tiles]}]
  (promise/spawn
    (fn []
      (render/render
        (mapv util/->raster-layer layers)
        {:tile-size        tile-size
         :view-width       viewport-w
         :view-height      viewport-h
         :view-matrix      view-matrix
         :dirty-tiles      image-dirty-tiles
         :image-width      image-width
         :image-height     image-height}))))

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

(defrecord CompositeNode [layer-ids state-atom]
  proto/IRenderNode
  (node-key [_]
    (into [(context/context-key)] layer-ids))

  (set-caching! [_ b]
    (swap! state-atom assoc :caching (boolean b)))

  (caching? [_]
    (boolean (:caching @state-atom)))

  (cached? [_]
    (some? (:composited-canvas @state-atom)))

  (invalidate-cache! [_]
    (clear-cache! state-atom))

  cg/INode
  (node-id [this] (proto/node-key this))
  (dependencies [this] (proto/node-key this))
  (compute [this inputs]
    (if-let [cached-canvas (:composited-canvas @state-atom)]
      ;; ── 命中缓存——直接返回 ──────────────────
      (promise/resolved
        (viewport-layer/make-viewport-layer
          (composite-viewport-id this)
          cached-canvas))

      ;; ── 未命中——并行请求 inputs → 合成 → 更新缓存 ──
      (promise/plet
        [composited (render-composite! (rest inputs) (first inputs))]
        ;; body——最后返回 ViewportLayer
        (update-cache! state-atom composited)
        (viewport-layer/make-viewport-layer
          (composite-viewport-id this)
          composited
          )))))

(defn make-composite-node [layer-nodes]
  (->CompositeNode  (mapv #(cg/node-id %) layer-nodes)
                    (atom (make-state))))
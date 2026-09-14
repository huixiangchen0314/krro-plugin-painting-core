(ns top.kzre.krro.plugin.painting.core.schedule.composite
  "通用合成节点"
  (:require
   [top.kzre.krro.core.util.promise :as promise]
   [top.kzre.krro.plugin.painting.core.model.tiled-image :as tiled-image]
   [top.kzre.krro.plugin.painting.core.render :as render]
   [top.kzre.krro.plugin.painting.core.schedule.protocol :as proto]
   [top.kzre.krro.plugin.painting.core.schedule.viewport-layer :as viewport-layer])
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

(defn- render-composite-promise
  "把回调式的 request-render-viewport! 包装为 Promise。

   在回调里 resolve p——返回 p。
   满足 then 的类型契约：a -> Promise<b>。"
  [node ctx layers]
  (let [composited (:canvas ctx)]
    (->
      (render/render-layers-viewport!
        (proto/node-key node)
        (tiled-image/->TiledImage composited
                                  (-> ctx :canvas-data :width)
                                  (-> ctx :canvas-data :height))
        (mapv #(:layer %) layers)
        (:dirty-tiles ctx)
        (:dirty-transform ctx)
        (:viewport ctx)
        (:viewport-w ctx)
        (:viewport-h ctx))
      (promise/fmap 
        (fn [_] composited)))))

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
        (viewport-layer/->ViewportLayer
          (composite-viewport-id this)
          cached-canvas
          1.0
          :normal))

      ;; ── 未命中——并行请求 inputs → 合成 → 更新缓存 ──
      (promise/plet
        [layers     (promise/all (mapv #(proto/request! % ctx) layer-nodes))
         composited (render-composite-promise this ctx layers)]
        ;; body——最后返回 ViewportLayer
        (update-cache! state-atom composited)
        (viewport-layer/->ViewportLayer
          (composite-viewport-id this)
          composited
          1.0
          :normal)))))

(defn make-composite-node [layer-nodes]
  (->CompositeNode  layer-nodes (atom (make-state))))
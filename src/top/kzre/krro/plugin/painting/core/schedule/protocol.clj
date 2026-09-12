(ns top.kzre.krro.plugin.painting.core.schedule.protocol
  "渲染调度节点协议与数据结构。"
  (:require
   [top.kzre.krro.plugin.painting.core.viewport :refer [ViewPort]])
  (:import
    (java.util.concurrent CompletableFuture)
    (top.kzre.krro.plugin.painting.core.viewport ViewPort)))

;; ═══════════════════════════════════════════════
;; 质量
;; ═══════════════════════════════════════════════


(def quality-values #{:preview :submit})

(defn valid-quality? [q]
  (contains? quality-values q))

;; ═══════════════════════════════════════════════
;; RenderContext
;; ═══════════════════════════════════════════════

(defrecord RenderContext
  [^ViewPort viewport
   ^int viewport-width
   ^int viewport-height
   dirty-tiles
   quality
   current-layer-id])

(defn make-render-context
  [viewport-transform viewport-width viewport-height
   dirty-tiles quality current-layer-id]
  (->RenderContext viewport-transform
                   viewport-width
                   viewport-height
                   dirty-tiles
                   quality
                   current-layer-id))

;; ═══════════════════════════════════════════════
;; ILayer
;; ═══════════════════════════════════════════════

(defprotocol ILayer
  "图层抽象。既用于源图层，也用于中间节点输出。"
  (layer-id [_] "图层 id（Keyword）")
  (canvas [_] "图层画布（TiledCanvas）")
  (transform [_] "图层仿射变换矩阵（float[]）")
  (visible? [_] "图层是否可见")
  (layer-opacity [_] "图层不透明度")
  (blend-mode [_] "图层混合模式"))

;; ═══════════════════════════════════════════════
;; IRenderNode
;; ═══════════════════════════════════════════════

(defprotocol IRenderNode
  "渲染节点抽象。每个节点可独立请求渲染，支持缓存。"

  (node-key [_]
    "节点身份 key。用于图重建时 diff 复用。
     通常形如 [:blend bottom-key top-key mode opacity]。")

  (inputs [_]
    "上游节点列表。")

  (set-caching! [_ flag]
    "通知节点是否进行缓存。")

  (caching? [_]
    "节点是否被配置为缓存。")

  (cached? [_]
    "节点当前是否已缓存有效结果。")

  (invalidate-cache! [_]
    "强制使缓存失效。")

  (request! [_ context]
    "执行渲染请求。返回 CompletableFuture<ILayer>。
     同步节点：completedFuture。
     异步节点：手动 complete 或 supplyAsync。"))

(defprotocol IRenderScheduler
  (scheduler-id [_] "调度器id，兼做渲染任务id"))

;; ═══════════════════════════════════════════════
;; 便捷函数
;; ═══════════════════════════════════════════════

(defn sync-result
  "把同步结果包装为 CompletableFuture。"
  [^ILayer layer]
  (CompletableFuture/completedFuture layer))

(defn sync-compute
  "把同步计算包装为 render 的返回。
   用法：(sync-compute ctx #(do-compute ...))"
  [f]
  (CompletableFuture/completedFuture (f)))

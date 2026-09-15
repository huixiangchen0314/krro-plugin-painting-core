(ns top.kzre.krro.plugin.painting.core.schedule.protocol
  "渲染调度节点协议与数据结构。")

;; ═══════════════════════════════════════════════
;; 质量
;; ═══════════════════════════════════════════════

(def quality-values #{:preview :submit})

(defn valid-quality? [q]
  (contains? quality-values q))


;; ═══════════════════════════════════════════════
;; ILayer
;; ═══════════════════════════════════════════════

;; 计算结果应当包含
;; layer 变换到视口的图层
;; 其他调度分析信息
(defprotocol ILayer
  "图层抽象。既用于源图层，也用于中间节点输出。"
  (layer-id [_] "图层 id（Keyword）")
  (canvas [_] "图层画布（TiledCanvas）")
  (transform [_] "图层仿射变换矩阵（float[]）")
  (visible? [_] "图层是否可见")
  (opacity [_] "图层不透明度")
  (blend-mode [_] "图层混合模式"))

;; ═══════════════════════════════════════════════
;; IRenderNode
;; ═══════════════════════════════════════════════

;; 这协议用于管理，无需管理就不要实现
(defprotocol IRenderNode
  "渲染节点抽象。每个节点可独立请求渲染，支持缓存。"

  (node-key [_]
    "节点身份 key。用于图重建时 diff 复用。
     通常形如 [:blend bottom-key top-key mode opacity]。")

  (set-caching! [_ flag]
    "通知节点是否进行缓存。")

  (caching? [_]
    "节点是否被配置为缓存。")

  (cached? [_]
    "节点当前是否已缓存有效结果。")

  (invalidate-cache! [_]
    "强制使缓存失效。")
  (migrate [_ other change] "从另外一个渲染节点迁移缓存"))

(defprotocol IRenderScheduler
  (set-layers! [_ layers])
  (render! [_ ctx]
    "执行一次渲染,
  返回 Promise<IPersistentMap>. \n
  {:canvas canvas ;; 最终画布，该画布所有权归用户，请自行释放 \n
  :dirty-tiles dirty-tiles ;; 裁剪过后的脏矩形，合并前请先删除这块区域 \n
  "))
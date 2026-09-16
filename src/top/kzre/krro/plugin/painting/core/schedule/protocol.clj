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

(defprotocol ILayer
  "图层抽象。既用于源图层，也用于中间节点输出。"
  (layer-id [_] "图层 id（Keyword）")
  (canvas [_] "图层画布（TiledCanvas）")
  (transform [_] "图层仿射变换矩阵（float[]）")
  (visible? [_] "图层是否可见")
  (opacity [_] "图层不透明度")
  (blend-mode [_] "图层混合模式"))


(defprotocol ICachingNode
  "缓存节点——支持缓存价值评估和生命周期管理。

   实现者是一个计算图节点（cg/INode），额外承担缓存职责：
     - 报告缓存价值（供评估器 Top-K 选择）
     - 报告内存/显存开销
     - 响应 caching 开关
     - 从旧节点迁移缓存

   节点身份由 cg/INode/node-id 提供——本协议不再重复声明。
   缓存有效性由节点的 migrate 自行判断——对比 old 与自身状态。"

  (set-caching! [_ flag]
    "通知节点是否进行缓存。")

  (caching? [_]
    "节点是否被配置为缓存。")

  (cached? [_]
    "节点当前是否已缓存有效结果。")

  (invalidate-cache! [_]
    "强制使缓存失效。")

  (cache-value [_]
    "该节点的缓存价值——正值表示值得缓存。
     供评估器排序——全局 Top-K 选择。")

  (mem-cost [_]
    "缓存的内存开销——字节。")

  (vmem-cost [_]
    "缓存的显存开销——字节。")

  (migrate [_ other change]
    "从另一个缓存节点迁移缓存。

     调用时机：diff 时 node-id 相同——但内容可能已变。
     节点自己决定：
       - 对比 old 的状态——内容是否一致
       - 一致 → 迁移缓存
       - 不一致 → 丢弃旧缓存——compute 时重算

     other  —— 同 node-id 的旧节点
     change —— 到达该节点的变化——节点据此判断"))

(defprotocol IRenderScheduler
  (set-layers! [_ layers])
  (render! [_ ctx]
    "执行一次渲染,
  返回 Promise<IPersistentMap>.

  {:canvas canvas ;; 最终画布，该画布所有权归用户，请自行释放.

  :dirty-tiles dirty-tiles ;; 裁剪过后的脏矩形，合并前请先删除这块区域

  "))
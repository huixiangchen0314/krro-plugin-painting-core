(ns top.kzre.krro.plugin.painting.core.changes.composite
  "复合变化——组合多个异构 change。

   当两个 change 属于不同类型时——无法合并为单一类型——
   用 CompositeChange 包裹它们。

   语义：
     - seeds         = 所有 components 的 seeds 并集
     - combine       = 追加或合并另一个 CompositeChange
     - empty-change? = 所有 components 都为空

   使用场景：
     - 调度器合并多个 op：stroke + pan —— 两个 change
     - 传播中节点收到多波 change：viewport + layer —— 组合
     - migrate 检测 components——按需分别处理"
  (:require
    [top.kzre.krro.core.util.diff :as diff]))

;; ═══════════════════════════════════════════════
;; CompositeChange
;; ═══════════════════════════════════════════════

(defrecord CompositeChange [changes]
  diff/IChange

  (seeds [_]
    (into #{} (mapcat diff/seeds) changes))

  (combine [this other]
    (cond
      ;; ── 空——保持自己
      (diff/empty-change? other)
      this

      ;; ── 另一个 Composite——展平合并
      (instance? CompositeChange other)
      (->CompositeChange (into changes (:changes other)))

      ;; ── 单类型 change——追加
      :else
      (->CompositeChange (conj changes other))))

  (empty-change? [_]
    (every? diff/empty-change? changes)))

;; ═══════════════════════════════════════════════
;; 构造器
;; ═══════════════════════════════════════════════


(defn composite-change
  "从 change 集合构造复合 change。"
  [cs]
  (->CompositeChange (vec cs)))

(defn empty-composite
  "空复合——单位元。"
  []
  (->CompositeChange []))

(defn changes [^CompositeChange change]
  (:changes change))


;; ═══════════════════════════════════════════════
;; 辅助——检查是否包含某类 change
;; ═══════════════════════════════════════════════

(defn contains-type?
  "复合变化中是否包含指定类型的 change。
   用于 migrate 里按需分支——比如 '有 ViewportPan 吗'。"
  [^CompositeChange composite type]
  (boolean (some #(instance? type %) (:changes composite))))

(defn find-type
  "复合变化中第一个指定类型的 change——没有返回 nil。"
  [^CompositeChange composite type]
  (first (filter #(instance? type %) (:changes composite))))
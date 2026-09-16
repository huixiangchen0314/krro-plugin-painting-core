(ns top.kzre.krro.plugin.painting.core.schedule.evaluate
  "缓存评估——Top-K 选择 + BFS 设置原子层缓存。

   above-nodes 里的每个节点通过 ICachingNode 协议自报：
     cache-value —— 缓存价值
     mem-cost    —— 内存占用
     vmem-cost   —— 显存占用

   算法：
     1. Top-K——按价值降序——预算允许下选择顶层节点
     2. set-caching! true——标记选中节点——预算已扣除
     3. BFS 沿 cg/dependencies 遍历到原子层
        - 遇到未访问节点——检查预算——够则 set-caching! true 并扣预算
        - 预算不足——跳过——不设置缓存
        - visited 防 DAG 重复

   总占用保证 ≤ max-mem / max-vmem——无论 Top-K 还是 BFS。
   "
  (:require
    [top.kzre.krro.core.util.computing-graph :as cg]
    [top.kzre.krro.plugin.painting.core.schedule.protocol :as proto])
  (:import
    (java.util Set HashSet)
    (top.kzre.krro.core.util.computing_graph ComputingGraph)
    (top.kzre.krro.plugin.painting.core.schedule.protocol ICachingNode)))

;; ═══════════════════════════════════════════════
;; 顶层
;; ═══════════════════════════════════════════════

(defn evaluate!
  "对 above-nodes 做缓存评估——Top-K + BFS 设置原子层缓存。

   参数：
     graph    —— ComputingGraph
     nodes    —— 候选节点序列——每个节点应实现 ICachingNode
     max-mem  —— 内存预算
     max-vmem —— 显存预算

   返回 java.util.Set——被缓存的节点集合（含 Top-K 选中的和 BFS 设置的）。"
  ^Set
  [^ComputingGraph graph nodes {:keys [max-mem max-vmem]}]
  (let [nodes-map (cg/nodes graph)
        max-mem   (long max-mem)
        max-vmem  (long max-vmem)

        ;; ── 预算——Top-K 和 BFS 共享
        budget (atom {:mem max-mem :vmem max-vmem})
        cached (atom (HashSet.))
        visited (atom #{})

        ;; ── 尝试缓存节点——检查预算——够则设置并扣减
        try-cache!
        (fn [node]
          (when (instance? ICachingNode node)
            (let [nid (cg/node-id node)]
              (when-not (contains? @visited nid)
                (swap! visited conj nid)
                (let [m  (long (proto/mem-cost node))
                      vm (long (proto/vmem-cost node))
                      b  @budget]
                  (when (and (<= m (:mem b)) (<= vm (:vmem b)))
                    (proto/set-caching! node true)
                    (.add ^HashSet @cached node)
                    (swap! budget #(-> %
                                       (update :mem  - m)
                                       (update :vmem - vm)))
                    true))))))]

    ;; ── 阶段 1：Top-K——按价值降序
    (let [candidates
          (into []
                (comp
                  (filter #(instance? ICachingNode %))
                  (keep (fn [node]
                          (let [v  (double (proto/cache-value node))
                                m  (long (proto/mem-cost node))
                                vm (long (proto/vmem-cost node))]
                            (when (and (pos? v)
                                       (<= m max-mem)
                                       (<= vm max-vmem))
                              {:node node :value v :mem m :vmem vm})))))
                nodes)

          sorted (sort-by :value > candidates)]

      (doseq [{:keys [node]} sorted]
        (try-cache! node)))

    ;; ── 阶段 2：BFS 沿依赖遍历——预算剩余则设置
    (loop [frontier (vec (keep nodes-map
                               (mapcat cg/dependencies (seq @cached))))]
      (when (seq frontier)
        (let [cacheable (filter #(instance? ICachingNode %) frontier)
              sorted (sort-by #(proto/cache-value %) > cacheable)
              next-frontier
              (reduce
                (fn [acc node]
                  (let [nid (cg/node-id node)]
                    (if (contains? @visited nid)
                      acc
                      ;; try-cache! 内部会标记 visited + 尝试设置缓存
                      (do
                        (try-cache! node)
                        (into acc (keep nodes-map (cg/dependencies node)))))))
                []
                sorted)]
          (recur next-frontier))))

    @cached))
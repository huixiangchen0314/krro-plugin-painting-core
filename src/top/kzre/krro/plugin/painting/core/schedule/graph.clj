(ns top.kzre.krro.plugin.painting.core.schedule.graph
  (:require
   [top.kzre.krro.canvas.core.layer.group :as group]
   [top.kzre.krro.canvas.core.layer.path :as path]
   [top.kzre.krro.core.core :as kcc]
   [top.kzre.krro.core.util.computing-graph :as cg]
   [top.kzre.krro.core.util.diff :as diff]
   [top.kzre.krro.plugin.painting.core.schedule.composite :as composite]
   [top.kzre.krro.plugin.painting.core.schedule.context :as context]
   [top.kzre.krro.plugin.painting.core.schedule.protocol :as proto]
   [top.kzre.krro.plugin.painting.core.schedule.raster-layer :as raster-layer]
   [top.kzre.krro.plugin.painting.core.schedule.result :as result]
   [top.kzre.krro.plugin.painting.core.schedule.util :as schedule.util]
   [top.kzre.krro.canvas.core.layer.util :as util])
  (:import
   (top.kzre.krro.core.util.computing_graph ComputingGraph)
   (top.kzre.krro.core.util.diff IDiff)))

(defn pass-though
  "图层组穿透，目前没有引用图层，先仅根据图层属性进行穿透处理"
  [layers]
  (mapv group/pass-through layers))

(declare build-level)


;; ═══════════════════════════════════════════════
;; 叶子图层构建——多方法分派
;; ═══════════════════════════════════════════════

(defmulti build-leaf
          "构建叶子图层节点。按 :type 分派。

           仅处理叶子图层——组由 build-atom 特判后走 build-level。

           方法签名：
             (build-leaf atom ctx)
               atom —— 叶子图层
               ctx  —— 上下文

           返回 {:nodes [...] :root node}"
          (fn [atom _] (:type atom)))

(defmethod build-leaf :raster
  [atom _]
  (let [node (raster-layer/make-raster-layer-node atom)]
    {:nodes [node]
     :root  node}))

(defmethod build-leaf :default
  [atom _]
  (throw (ex-info "unknown leaf layer type for build-leaf"
                  {:layer-id (:id atom)
                   :type     (:type atom)})))

;; ═══════════════════════════════════════════════
;; 组/原子的统一入口——特判组
;; ═══════════════════════════════════════════════

(defn- build-atom
  "构建单个原子。

   组 → 递归 build-level——消费 inner-current-path
   其他 → build-leaf——不需要 inner-current-path

   inner-current-path —— 若 current 在该组内部——组内相对路径
                         否则 nil"
  [atom inner-current-path ctx]
  (if (group/group? atom)
    (build-level (group/layers atom) inner-current-path ctx)
    (build-leaf atom ctx)))


;; ═══════════════════════════════════════════════
;; 连续 normal 分段
;; ═══════════════════════════════════════════════

(defn- group-by-blend
  "把原子序列按连续 normal 合成分组。
   非 normal 原子单独成组。

   例：
     [n n m n n n m m]
     → [[n n] [m] [n n n] [m] [m]]"
  [atoms]
  (if (empty? atoms)
    []
    (loop [acc       []
           current   []
           remaining atoms]
      (if (empty? remaining)
        (if (seq current) (conj acc current) acc)
        (let [atom    (first remaining)
              normal? (util/normal-blend-mode? atom)]
          (cond
            (empty? current)
            (recur acc [atom] (rest remaining))

            (and normal? (util/normal-blend-mode? (first current)))
            (recur acc (conj current atom) (rest remaining))

            :else
            (recur (conj acc current) [atom] (rest remaining))))))))

;; ═══════════════════════════════════════════════
;; above 段——按 normal 分段 + 链式合成
;; ═══════════════════════════════════════════════

(defn- build-above-level
  "构建 above 段。

   输入：
     initial-input —— below 段的 root——作为第一段的初始输入——可为 nil
     layers        —— above 段的原子序列
     inner-path    —— current 在 above 段首原子内部的相对路径——nil 表示不在
     ctx

   分段规则：
     连续 normal 原子 → 合并成一个 CompositeNode
     非 normal 原子   → 单独一个 CompositeNode

   链式：每段以 prev-root 为第一个输入。

   返回 {:nodes [...] :root composite-node 或 nil}
     无图层时 root 为 nil——由调用方决定 fallback。"
  [initial-input layers inner-path ctx]
  (if (empty? layers)
    {:nodes [] :root nil}
    (let [groups (group-by-blend layers)]
      (loop [remaining   groups
             prev-root   initial-input
             all-nodes   []
             last-root   nil
             first-group true]
        (if (empty? remaining)
          {:nodes (vec all-nodes)
           :root  last-root}
          (let [group       (first remaining)
                head        (first group)
                tail        (vec (rest group))

                head-built  (build-atom head
                                        (when first-group inner-path)
                                        ctx)
                tail-built  (mapv #(build-atom % nil ctx) tail)

                seg-roots   (into [(:root head-built)]
                                  (map :root tail-built))
                seg-inputs  (if prev-root
                              (into [prev-root] seg-roots)
                              seg-roots)
                seg-comp    (composite/make-composite-node seg-inputs)

                seg-nodes   (vec (concat
                                   [seg-comp]
                                   (:nodes head-built)
                                   (mapcat :nodes tail-built)))]
            (recur (rest remaining)
                   seg-comp
                   (into all-nodes seg-nodes)
                   seg-comp
                   false)))))))

;; ═══════════════════════════════════════════════
;; 构建一层
;; ═══════════════════════════════════════════════

(defn- build-level
  "构建一层。

   layers       —— 该层图层列表（已经过 pass-though 预处理）
   current-path —— 相对于该层的当前路径——nil 表示该层无 current
   ctx

   分段：
     below = current 之前的所有兄弟——一个 CompositeNode
     above = current 及其后的兄弟——按连续 normal 再切子段——链式合成

   返回 {:nodes [...] :root composite-node}"
  [layers current-path ctx]
  (let [current-idx (when (seq current-path) (first current-path))
        inner-path  (when (seq current-path) (subvec current-path 1))

        below-layers (if current-idx
                       (subvec layers 0 current-idx)
                       layers)
        above-layers (if current-idx
                       (subvec layers current-idx)
                       [])

        below-built  (mapv #(build-atom % nil ctx) below-layers)
        below-roots  (mapv :root below-built)
        below-comp   (when (seq below-roots)
                       (composite/make-composite-node below-roots))

        above-built  (build-above-level below-comp above-layers inner-path ctx)
        above-comp   (:root above-built)

        root         (or above-comp below-comp)

        [root all-nodes]
        (if root
          [root
           (vec (concat
                  (when below-comp [below-comp])
                  (:nodes above-built)
                  (mapcat :nodes below-built)))]
          ;; 空栈兜底——root 和 nodes 同时构造
          (let [empty-root (composite/make-composite-node [])]
            [empty-root [empty-root]]))]
    {:nodes all-nodes
     :root  root}))

;; ═══════════════════════════════════════════════
;; 顶层入口
;; ═══════════════════════════════════════════════

(defn build-graph
  "构建计算图。

   流程：
     1. 应用视口变换
     2. 穿透处理——展开穿透组、过滤不可见图层
     3. 用 path 定位 current 在穿透后树中的路径
     4. 递归 build-level——current 分段 + 连续 normal 分段 + 递归组

   图结构：
     ctx-node
     └─→ build-level 的 root
             │
        result-node"
  ^ComputingGraph
  [layers {:keys [view-matrix current-layer-id] :as ctx}]
  (let [ctx-node     (context/make-context-node ctx)
        composed     (schedule.util/compose-transform layers view-matrix)
        throughed    (pass-though composed)

        current-path (when current-layer-id
                       (path/get-path throughed current-layer-id))
        {:keys [nodes root]} (build-level throughed current-path ctx)
        result-node  (result/make-result-node root)
        all-nodes    (into [ctx-node result-node] nodes)]
    (apply cg/graph all-nodes)))

(defrecord RenderGraphDiff []
  IDiff

  (outgoing [_ graph node-id]
    (get (cg/reverse-dependencies graph) node-id #{}))

  (order [_ graph affected-ids]
    (:sorted (kcc/topo-sort (fn [nid]
                          (cg/dependencies (get (cg/nodes graph) nid)))
                            affected-ids)))

  (inputs [_ graph node-id]
    (cg/dependencies (get (cg/nodes graph) node-id)))

  (migrate [_ graph node-id change old-value input-values]
    (let [new-node (get (cg/nodes graph) node-id)]
      ;; 从旧节点迁移缓存——node-key 相同时才有意义
      ;;   （diff 通过 node-id = node-key 保证这一点）
      (when (and old-value
                 (satisfies? proto/IRenderNode old-value)
                 (satisfies? proto/IRenderNode new-node))
        (proto/migrate new-node old-value change))
      new-node))

  (release [_ _graph old-value]
    ;; 被删除节点——释放资源
    (when (satisfies? proto/IRenderNode old-value)
      (proto/invalidate-cache! old-value))))

(defonce ^:private diff-spec* (->RenderGraphDiff))

(defn diff-spec [] diff-spec*)

(defn diff!
  "执行diff迁移到新图"
  [^ComputingGraph old-graph ^ComputingGraph new-graph changes]
  (if-not old-graph
    ;; 无旧计算图，直接用新的
    new-graph
    ;; 从旧计算图迁移
    (let [old-nodes (cg/nodes old-graph)
          ;; 取决与是否是原地改了，如果是原地改就不重建图了
          ;; 我们用原地改的方式节省重建开销，因为我们希望每个change小而独立
          _new-nodes (diff/diff (diff-spec) new-graph changes old-nodes)]
      new-graph)))

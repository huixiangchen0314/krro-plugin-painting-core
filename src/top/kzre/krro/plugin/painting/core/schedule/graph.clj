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
   [top.kzre.krro.plugin.painting.core.schedule.util :as schedule.util])
  (:import
   (top.kzre.krro.core.util.computing_graph ComputingGraph)
   (top.kzre.krro.core.util.diff IDiff)))

(defn pass-though
  "图层组穿透，目前没有引用图层，先仅根据图层属性进行穿透处理"
  [layers]
  (mapv group/pass-through layers))

(declare build-level)

(defn- build-atom
  "构建单个原子。

   普通图层 → RasterLayerNode
   非穿透组 → 递归 build-level——返回该组的 root

   inner-current-path —— 若 current 在该组内部——组内相对路径
                         否则 nil"
  [atom inner-current-path ctx]
  (if (group/group? atom)
    (build-level (group/layers atom) inner-current-path ctx)
    ;; 当前只处理光栅图层
    (let [node (raster-layer/make-raster-layer-node atom)]
      {:nodes [node]
       :root  node})))

(defn- build-level
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
        above-built  (vec
                       (map-indexed
                         (fn [i atom]
                           (build-atom atom
                                       (when (zero? i) inner-path)
                                       ctx))
                         above-layers))

        below-roots  (mapv :root below-built)
        below-comp   (when (seq below-roots)
                       (composite/make-composite-node below-roots))

        above-roots  (mapv :root above-built)
        above-inputs (cond-> []
                             below-comp        (conj below-comp)
                             (seq above-roots) (into above-roots))
        above-comp   (when (seq above-inputs)
                       (composite/make-composite-node above-inputs))

        ;; ── root——空栈时兜底构造，并纳入 nodes
        [root all-nodes]
        (if (or below-comp above-comp)
          [(or above-comp below-comp)
           (vec (concat
                  (when below-comp [below-comp])
                  (when above-comp [above-comp])
                  (mapcat :nodes below-built)
                  (mapcat :nodes above-built)))]
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
     4. 递归 build-level——按 current 分段 + 递归处理嵌套组

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

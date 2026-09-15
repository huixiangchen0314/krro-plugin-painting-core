(ns top.kzre.krro.plugin.painting.core.schedule.graph
  (:require
    [top.kzre.krro.core.core :as kcc]
    [top.kzre.krro.core.util.computing-graph :as cg]
    [top.kzre.krro.plugin.painting.core.schedule.composite :as composite]
    [top.kzre.krro.plugin.painting.core.schedule.protocol :as proto]
    [top.kzre.krro.plugin.painting.core.schedule.raster-layer :as raster-layer]
    [top.kzre.krro.plugin.painting.core.schedule.result :as result]
    [top.kzre.krro.plugin.painting.core.schedule.util :as schedule.util]
    [top.kzre.krro.canvas.core.layer.group :as group]
    [top.kzre.krro.core.util.diff :as diff]
    [top.kzre.krro.plugin.painting.core.schedule.context :as context])
  (:import
    (top.kzre.krro.core.util.computing_graph ComputingGraph)
    (top.kzre.krro.core.util.diff IChange IDiff)))


(defn build-graph
 ^ComputingGraph
  [layers {:keys [view-matrix] :as ctx}]
  (let [ctx-node (context/make-context-node ctx)
        composed (schedule.util/compose-transform layers view-matrix)
        throughed (mapv group/pass-through composed)
        layer-nodes     (mapv raster-layer/make-raster-layer-node throughed)
        composite-node  (composite/make-composite-node layer-nodes)
        result-node (result/make-result-node composite-node)
        nodes (into [ctx-node result-node
                     composite-node ]
                    layer-nodes)]
    (apply cg/graph nodes)))

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

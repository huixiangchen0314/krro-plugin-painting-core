(ns top.kzre.krro.plugin.painting.core.schedule.scheduler-impl
  (:require
   [top.kzre.krro.core.util.computing-graph :as cg]
   [top.kzre.krro.core.util.promise :as promise]
   [top.kzre.krro.plugin.painting.core.schedule.context :as context]
   [top.kzre.krro.plugin.painting.core.schedule.evaluate :as evaluate]
   [top.kzre.krro.plugin.painting.core.schedule.graph :as graph]
   [top.kzre.krro.plugin.painting.core.schedule.layer-impl]
   [top.kzre.krro.plugin.painting.core.schedule.protocol :as proto]
   [top.kzre.krro.plugin.painting.core.schedule.result :as result])
  (:import
    (java.lang AutoCloseable)
    (top.kzre.krro.core.util.computing_graph ComputingGraph)
    (top.kzre.krro.util.tile TiledCanvas)))


(defrecord SchedulerState [^ComputingGraph graph
                           layers
                           context])
(defn make-state
  ([]
   (map->SchedulerState {}))
  ([^ComputingGraph graph layers ctx]
   (->SchedulerState graph layers ctx)))


(defn diff! [{:keys [graph layers context]} new-context]
  (let [ctx-diff    (context/diff-info context new-context)
        new-ctx     (context/diff context new-context ctx-diff)
        building    (graph/build-graph layers new-ctx)
        new-graph   (:graph building)
        result-node (get (cg/nodes new-graph) (result/result-key))
        above-nodes (:above-nodes building)]
    ;; 1. 评估——设置新图各节点的 caching? 标志
    (evaluate/evaluate! new-graph above-nodes new-ctx)
    ;; 结果节点总是缓存
    (proto/set-caching! result-node true)
    (graph/diff! graph new-graph [])
    ;; 从结果节点中取出新脏瓦片，更新到上下文

    ;; 2. diff——migrate 根据新旧 caching?/cached? 状态迁移或释放
    (make-state new-graph layers new-ctx)))

(defrecord RenderScheduler [state-atom]
  proto/IRenderScheduler
  (set-layers! [_ layers]
    (swap! state-atom assoc :layers layers))
  (render! [_ ctx]
    (swap! state-atom diff! ctx)
    (let [{:keys [graph layers context]} @state-atom
          {:keys [tile-size view-dirty-tiles]} context]
      (if (and (seq layers) graph)
        ;; 返回的是差分画布，外部持有所有权
        (-> (cg/reduce-to graph (result/result-key))
            (cg/solve)
            (promise/fmap
              (fn [value-table]
            (let [layer (get value-table (result/result-key))
                  result {:canvas (.copy (proto/canvas layer))
                          :dirty-tiles view-dirty-tiles}]
              ;; 释放所有中间计算结果
              (doseq [v (vals value-table)]
                (cond
                  (instance? AutoCloseable v) (.close v)
                  :else nil))
              result))))
        {:canvas
         (doto
           (TiledCanvas. tile-size)
           (.setReadonly true))
         :dirty-tiles view-dirty-tiles})))
  AutoCloseable
  (close [_]
    (let [{:keys [graph]} @state-atom]
      ;; 1. 释放图里所有节点的缓存
      (when graph
        (doseq [node (vals (cg/nodes graph))]
          (when (satisfies? proto/ICachingNode node)
            (proto/invalidate-cache! node))))
      ;; 2. 重置状态——防止后续误用已释放的资源
      (reset! state-atom (make-state)))))

(defn make-scheduler
  "基于画布构建渲染调度器，注意该画布所有权被转移到调度器身上了"
  []
  (->RenderScheduler  (atom (make-state))))
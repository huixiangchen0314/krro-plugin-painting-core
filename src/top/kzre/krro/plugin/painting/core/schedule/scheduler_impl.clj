(ns top.kzre.krro.plugin.painting.core.schedule.scheduler-impl
  (:require
   [top.kzre.krro.plugin.painting.core.schedule.context :as context]
   [top.kzre.krro.plugin.painting.core.schedule.graph :as graph]
   [top.kzre.krro.plugin.painting.core.schedule.protocol :as proto])
  (:import
   [java.lang AutoCloseable]
   (top.kzre.krro.plugin.painting.core.schedule.protocol IRenderNode)
   (top.kzre.krro.util.tile TiledCanvas)))


(defrecord SchedulerState [^IRenderNode graph
                           context])

(defn make-state
  ([]
   (map->SchedulerState {}))
  ([^IRenderNode graph ctx]
   (->SchedulerState graph ctx)))

(defn diff! [{:keys [graph context]} layers new-context]
  (let [ctx-diff (context/diff-info context new-context)
        new-ctx (context/diff context new-context ctx-diff)
        new-graph (graph/build-graph layers new-ctx )]
    (make-state (graph/diff! graph new-graph ctx-diff) new-ctx)))

(defrecord RenderScheduler [^TiledCanvas canvas
                            state-atom]
  proto/IRenderScheduler
  (render! [_ layers ctx]
    (swap! state-atom diff! layers ctx)
    (let [{:keys [graph context]} @state-atom]
      (proto/request! graph context)))
  AutoCloseable
  (close [_]
    ;; 清理画布引用
    (.clear canvas)))

(defn make-scheduler
  "基于画布构建渲染调度器，注意该画布所有权被转移到调度器身上了"
  [^TiledCanvas canvas]
  (->RenderScheduler canvas (atom (make-state))))
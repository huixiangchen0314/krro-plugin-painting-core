(ns top.kzre.krro.plugin.painting.core.schedule.scheduler-impl
  (:require
   [taoensso.timbre :as log]
   [top.kzre.krro.core.util.computing-graph :as cg]
   [top.kzre.krro.core.util.promise :as promise]
   [top.kzre.krro.plugin.painting.core.layer.dispose :as dispose]
   [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
   [top.kzre.krro.plugin.painting.core.schedule.context :as context]
   [top.kzre.krro.plugin.painting.core.schedule.evaluate :as evaluate]
   [top.kzre.krro.plugin.painting.core.schedule.gl :as schedule.gl]
   [top.kzre.krro.plugin.painting.core.schedule.graph :as graph]
   [top.kzre.krro.plugin.painting.core.schedule.layer :as schedule.layer]
   [top.kzre.krro.plugin.painting.core.schedule.layer-impl]
   [top.kzre.krro.plugin.painting.core.schedule.protocol :as proto]
   [top.kzre.krro.plugin.painting.core.schedule.result :as result]
   [top.kzre.krro.plugin.painting.core.schedule.util :as util])
  (:import
   (java.lang AutoCloseable)
   (top.kzre.colorutils.color RGB)
   (top.kzre.krro.core.util.computing_graph ComputingGraph)
   (top.kzre.krro.util.tile TiledCanvas)))

(defrecord SchedulerState [^ComputingGraph graph
                           layers
                           context
                           gl-composite-context])

(defn- make-state []
  (map->SchedulerState {}))

(defn- ensure-facilities!
  "确保 state-atom 里的 :gl-composite-context 就绪。

   已有则原样返回（Promise<Atom>）。
   没有则在 GL 线程构建，成功后 swap! 写入 state-atom。

   返回 Promise<Atom>——解析后 :gl-composite-context 已就绪。"
  [{:keys [config state-atom]
    :as _scheduler}]
  (let [backend (:backend config)]
    (case backend
      :gl
      (if (some? (:gl-composite-context @state-atom))
        (promise/resolved state-atom)
        (->  (:gl-composite-context-builder config)
             (.build)
             (promise/from-completable-future)
             (promise/fmap
               (fn [composite-ctx]
                 (swap! state-atom assoc :gl-composite-context composite-ctx)
                 state-atom))))
      (promise/resolved state-atom))))

(defn- diff-layers!
  [state-atom new-layers]
  (if-let [layers (:layers @state-atom)]
    (do
      (schedule.layer/diff! layers new-layers)
      (swap! state-atom assoc :layers new-layers)
      (dispose/dispose-layers! layers))
    (swap! state-atom assoc :layers new-layers)))

(defn- diff-context
  [{:keys [config state-atom] :as _scheduler}
   new-context]
  (let [{:keys [context gl-composite-context]} @state-atom
        ctx-diff    (context/diff-info context new-context)
        new-ctx
        (-> (context/diff context new-context ctx-diff)
            (assoc :gl-composite-context gl-composite-context)
            (merge (select-keys
                     config
                     [:backend])))]
    new-ctx))

(defn diff-state!
  [{:keys [graph layers] :as state}
   scheduler
   new-ctx]
  (let [building    (graph/build-graph layers scheduler new-ctx)
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
    (assoc state :graph new-graph
                 :context new-ctx)))

(defn- empty-backdrop [{:keys [backdrop] :as _scheduler}]
  (TiledCanvas.
    (.getTileSize ^TiledCanvas backdrop)
    (.getDefaultPixel ^TiledCanvas backdrop)))

(defrecord RenderScheduler
  [config
   state-atom
   ^TiledCanvas backdrop]
  proto/IRenderScheduler
  (set-layers! [_ layers]
    (diff-layers! state-atom layers))
  (render! [this ctx]
    (-> (ensure-facilities! this)
        (promise/then
          (fn [_]
            (let [new-ctx (diff-context this ctx)]
              (swap! state-atom diff-state! this new-ctx))
            (let [{:keys [graph layers]} @state-atom]
              (if (and (seq layers) graph)
                (-> (cg/reduce-to graph (result/result-key))
                    (cg/solve)
                    (promise/handle
                      (fn [v e]
                        (when e
                          (log/error e "schedule failed"))
                        v))
                    (promise/fmap
                      (fn [computed]
                        (let [result
                              (.copy (proto/canvas (get computed (result/result-key))))]
                          ;; 保险起见的最终清理清理
                          (apply util/close-safely (vals computed))
                          result))))
                (promise/resolved
                  (empty-backdrop this))))))))
  AutoCloseable
  (close [_]
    (let [{:keys [graph gl-composite-context layers]} @state-atom]
      ;; 释放图里所有节点的缓存
      (when graph
        (doseq [node (vals (cg/nodes graph))]
          (try
            (when (satisfies? proto/ICachingNode node)
              (proto/invalidate-cache! node))
            (catch Throwable t
              (log/error t "invalidate-cache failed!")))))
      (util/close-safely gl-composite-context backdrop)
      (dispose/dispose-layer layers)
      ;; 重置状态——防止后续误用已释放的资源
      (reset! state-atom (make-state)))))

(defn make-scheduler
  "基于画布构建渲染调度器，注意该画布所有权被转移到调度器身上了"
  ([]
   (make-scheduler {}))
  ([config]
   (let
     [polyfill
      (cond->
        config
        ;; 使用 gl 合成
        (= :gl (:backend config))
        (assoc
          ;; 指定合成上下文，或者使用默认的 lwjgl glfw 离屏渲染上下文
          :gl-composite-context-builder
          (:gl-composite-context-builder config
            (schedule.gl/default-composite-context-builder))
          ))]
     (->RenderScheduler
       polyfill
       (atom (make-state))
       (TiledCanvas. pc/global-tile-size
                     (RGB/rgba 0 0 0 0))))))

;; TODO 1 新的享元api
;; TODO 2 修整节点，避免依赖结果
;; TODO 3
(ns top.kzre.krro.plugin.painting.core.schedule.scheduler-impl
  (:require
   [taoensso.timbre :as log]
   [top.kzre.krro.core.util.computing-graph :as cg]
   [top.kzre.krro.core.util.promise :as promise :refer [plet]]
   [top.kzre.krro.plugin.painting.core.schedule.context :as context]
   [top.kzre.krro.plugin.painting.core.schedule.evaluate :as evaluate]
   [top.kzre.krro.plugin.painting.core.schedule.graph :as graph]
   [top.kzre.krro.plugin.painting.core.schedule.layer-impl]
   [top.kzre.krro.plugin.painting.core.schedule.protocol :as proto]
   [top.kzre.krro.plugin.painting.core.schedule.result :as result]
   [top.kzre.krro.plugin.painting.core.project.canvas :as pc])
  (:import
    (java.lang AutoCloseable)
    (top.kzre.colorutils.color RGB)
    (top.kzre.krro.canvas.gl GLExecutors)
    (top.kzre.krro.canvas.gl.composite GLCompositeContextBuilder)
    (top.kzre.krro.canvas.gl.resource NoGLTexturePool PixelFormat)
    (top.kzre.krro.canvas.gl.tile GLRgba8AtlasFactory GLTiledTextureLayout)
    (top.kzre.krro.core.util.computing_graph ComputingGraph)
    (top.kzre.krro.util.tile TiledCanvas)))



(defrecord SchedulerState [^ComputingGraph graph
                           layers
                           context
                           gl-composite-context])
(defn make-state []
  (map->SchedulerState {}))


(defn ensure-gl-composite-context!
  "确保 state-atom 里的 :gl-composite-context 就绪。

   已有则原样返回（Promise<Atom>）。
   没有则在 GL 线程构建，成功后 swap! 写入 state-atom。

   返回 Promise<Atom>——解析后 :gl-composite-context 已就绪。"
  [state-atom]
  (if (some? (:gl-composite-context @state-atom))
    (promise/resolved state-atom)
    (let [tile-size pc/global-tile-size
          executor  (GLExecutors/offscreen)
          layout    (GLTiledTextureLayout. 8 (int tile-size) 4)
          tex-pool  (NoGLTexturePool. executor)
          factory   (GLRgba8AtlasFactory. layout tex-pool)]
      (-> (GLCompositeContextBuilder.)
          (.glExecutor executor)
          (.tileSize (int tile-size))
          (.atlasCapacity 3)
          (.atlasFactory factory)
          (.viewFboPoolCapacity 4)
          (.pixelFormat PixelFormat/RGBA8)
          (.build)
          (promise/from-completable-future)
          (promise/fmap
            (fn [composite-ctx]
              (swap! state-atom assoc :gl-composite-context composite-ctx)
              state-atom))))))

(defn diff! [{:keys [graph layers context gl-composite-context] :as state} new-context]
  (let [ctx-diff    (context/diff-info context new-context)
        new-ctx     (-> (context/diff context new-context ctx-diff)
                      (assoc :gl-composite-context gl-composite-context))
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
    (assoc state :graph new-graph
                 :context new-ctx)))

(defrecord RenderScheduler [state-atom]
  proto/IRenderScheduler
  (set-layers! [_ layers]
    (swap! state-atom assoc :layers layers))
  (render! [this ctx]
    (-> (ensure-gl-composite-context! state-atom)
        (promise/then
          (fn [_]
            (swap! state-atom diff! ctx)
            (let [{:keys [graph layers context]} @state-atom
                  {:keys [tile-size view-dirty-tiles]} context]
              (if (and (seq layers) graph)
                ;; 返回的是差分画布，外部持有所有权
                (-> (cg/reduce-to graph (result/result-key))
                    (cg/solve)
                    (promise/fmap
                      (fn [computed]
                        (let [layer (get computed (result/result-key))
                              result {:canvas (.copy (proto/canvas layer))
                                      :dirty-tiles view-dirty-tiles}]
                          (doseq [v (vals computed)]
                            (when (instance? AutoCloseable v)
                              (.close v)))
                          result))))
                (promise/resolved
                  {:canvas
                   (doto
                     (TiledCanvas. tile-size (RGB/rgba 0 0 0 0))
                     (.setReadonly true))
                   :dirty-tiles view-dirty-tiles})))))))
  AutoCloseable
  (close [_]
    (let [{:keys [graph gl-composite-context]} @state-atom]
      ;; 1. 释放图里所有节点的缓存
      (when graph
        (doseq [node (vals (cg/nodes graph))]
          (when (satisfies? proto/ICachingNode node)
            (proto/invalidate-cache! node))))
      (when gl-composite-context
        (.close ^AutoCloseable gl-composite-context))
      ;; 2. 重置状态——防止后续误用已释放的资源
      (reset! state-atom (make-state)))))

(defn make-scheduler
  "基于画布构建渲染调度器，注意该画布所有权被转移到调度器身上了"
  []
  (->RenderScheduler  (atom (make-state))))
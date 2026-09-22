(ns top.kzre.krro.plugin.painting.core.edit.vector-brush
  (:require
    [top.kzre.krro.core.reframe.core :as rf]
    [top.kzre.krro.core.reframe.transaction :as tx :refer [transaction-interceptor]]
    [top.kzre.krro.plugin.painting.core.brush.core :as brush]
    [top.kzre.krro.plugin.painting.core.edit.interceptors :refer [cleanup-tool-interceptor
                                                                 tool-context-interceptor
                                                                 tool-context-key]]
    [top.kzre.krro.plugin.painting.core.edit.protocol :as p]
    [top.kzre.krro.plugin.painting.core.store :as store]
    [top.kzre.krro.plugin.painting.core.transactions.vector-stroke :as tx-vector-stroke]))


(defrecord VectorBrushState [stroke layer-backup layer-transform layer-transform-inv]
  p/IToolData
  (dispatch-event [_ event-map cofx] )
  ;; 工具状态不该持有非托管数据，事务持有
  (cleanup! [_ _] nil)
  (overlay [_ _] []))

(defn make-state
  []
  (map->VectorBrushState {}))

(rf/reg-event-fx
  store/app-id :vector-brush-tool/press
  [(cleanup-tool-interceptor VectorBrushState :factory (fn [_] (make-state)))
   (tool-context-interceptor)
   (transaction-interceptor)]
  (fn [cofx _]
    (let [{:keys [layer-id layer]} (get cofx (tool-context-key))]
      (cond
        (nil? layer-id)
        {:fx [[:warn "No active layer!"]]}

        (not= :vector (:type layer))
        {:fx [[:warn "Brush tool is only used for vector layer!"]]}

        :else
        {:transaction [(tx/begin-transaction
                         (tx-vector-stroke/kind)
                         :style
                         {:stroke {:color (brush/get-global-brush-color)
                                   :width 15
                                   :cap   :round
                                   :join  :round}})]}))))

(rf/reg-event-fx
  store/app-id :vector-brush-tool/drag
  [(tool-context-interceptor)
   (transaction-interceptor)]
  (fn [cofx _]
    (let [{:keys [layer-event]} (get cofx (tool-context-key))]
      {:transaction [(tx/transaction-operation
                       (tx-vector-stroke/kind) :drag
                       :layer-event layer-event)]
       :fx []})))


(rf/reg-event-fx
  store/app-id :vector-brush-tool/release
  [(tool-context-interceptor)
   (transaction-interceptor)]
  (fn [_ _]
    {:transaction [(tx/commit-transaction (tx-vector-stroke/kind))]}))
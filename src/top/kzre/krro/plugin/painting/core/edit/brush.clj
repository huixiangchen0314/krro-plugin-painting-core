(ns top.kzre.krro.plugin.painting.core.edit.brush
  (:require
   [top.kzre.krro.core.reframe.core :as rf]
   [top.kzre.krro.core.reframe.transaction :as tx :refer [transaction-interceptor]]
   [top.kzre.krro.plugin.painting.core.edit.interceptors :refer [cleanup-tool-interceptor
                                                                 tool-context-interceptor
                                                                 tool-context-key]]
   [top.kzre.krro.plugin.painting.core.store :as store]
   [top.kzre.krro.plugin.painting.core.transactions.brush-stroke :as tx-brush-stroke])
  (:import
   (top.kzre.colorutils.color RGB)))

;; 移出edit 包，edit 只保留ui 状态机

(defn- cursor-overlay
  [{:keys [viewport event]}]
  (let [{:keys [zoom]} viewport]
    [[:cursor {:x      (:x event)
               :y      (:y event)
               :radius (* 10 zoom)
               :type   :circle
               :color  (RGB/rgba 1 0 0 1)}]]))


(rf/reg-event-fx
  store/app-id :brush-tool/press
  [(cleanup-tool-interceptor)
   (tool-context-interceptor)
   (transaction-interceptor)]
  (fn [cofx _]
    (let [{:keys [layer-id layer]} (get cofx (tool-context-key))]
      (cond
        (nil? layer-id)
        {:fx [[:warn "No active layer!"]]}

        (not= :raster (:type layer))
        {:fx [[:warn "Brush tool is only used for raster layer!"]]}

        :else
        {:transaction [(tx/begin-transaction (tx-brush-stroke/kind))]}))))


(rf/reg-event-fx
  store/app-id :brush-tool/drag
  [(tool-context-interceptor)
   (transaction-interceptor)]
  (fn [cofx [_ _ _ frame]]
    (let [ctx (get cofx (tool-context-key))]
      {:transaction [(tx/transaction-operation
                       (tx-brush-stroke/kind) :drag
                       :layer-event (:layer-event ctx))]
       :fx [[:tool/flush-overlay (cursor-overlay ctx) frame]]})))


(rf/reg-event-fx
  store/app-id :brush-tool/release
  [(tool-context-interceptor)
   (transaction-interceptor)]
  (fn [_ _]
    {:transaction [(tx/commit-transaction (tx-brush-stroke/kind))]}))

(rf/reg-event-fx
  store/app-id :brush-tool/move
  [(cleanup-tool-interceptor)
   (tool-context-interceptor)
   (transaction-interceptor)]
  (fn [cofx [_ _ _ frame]]
    (let [ctx (get cofx (tool-context-key))]
      {:fx [[:tool/flush-overlay (cursor-overlay ctx) frame]]})))
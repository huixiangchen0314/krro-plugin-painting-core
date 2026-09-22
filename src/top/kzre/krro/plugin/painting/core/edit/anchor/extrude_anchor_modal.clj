(ns top.kzre.krro.plugin.painting.core.edit.anchor.extrude-anchor-modal
  (:require
    [top.kzre.krro.core.reframe.core :as rf]
    [top.kzre.krro.core.reframe.transaction :as tx :refer [transaction-interceptor]]
    [top.kzre.krro.plugin.painting.core.edit.anchor.state :as anchor.state]
    [top.kzre.krro.plugin.painting.core.edit.interceptors :refer [cleanup-tool-interceptor
                                                                  get-tool-context
                                                                  tool-context-interceptor]]
    [top.kzre.krro.plugin.painting.core.store :as store]
    [top.kzre.krro.plugin.painting.core.transactions.anchor-extrude-modal :as anchor-extrude-modal])
  (:import (top.kzre.krro.plugin.painting.core.edit.anchor.state AnchorState)))

;; 进入顶点挤出模态
(rf/reg-event-fx
  store/app-id :anchor/enter-extrude-anchor-modal
  [(cleanup-tool-interceptor AnchorState :factory (fn [_] (anchor.state/make-anchor-state)))
   (tool-context-interceptor)
   (transaction-interceptor)]
  (fn [cofx _]
    (let [ctx       (get-tool-context cofx)
          {:keys [layer-type layer-event transaction-kind]} ctx
          tool-data (get-in (:record cofx) [:canvas-state :tool-data])
          {:keys [active-anchor]} tool-data]
      (cond
        (not= :vector layer-type)
        {:fx [[:warn (str "Anchor tool is invalid for " layer-type)]]}

        (nil? active-anchor)
        {:fx [[:warn "No active anchor"]]}

        (some? transaction-kind)
        {:fx [[:warn "Transaction already exists!"]]}

        :else
        {:record (-> (:record cofx)
                     (assoc-in [:canvas-state :tool-data :mode]  :extrude-anchor)
                     (assoc-in [:canvas-state :tool-data :modal] true))
         :transaction
         [(tx/begin-transaction
            (anchor-extrude-modal/kind)
            :anchor active-anchor
            :layer-event layer-event)]}))))

;; 挤出顶点 —— move 阶段
(rf/reg-event-fx
  store/app-id :anchor/extrude-anchor
  [(cleanup-tool-interceptor AnchorState :factory (fn [_] (anchor.state/make-anchor-state)))
   (tool-context-interceptor)
   (transaction-interceptor)]
  (fn [cofx [_ _record-id _ frame]]
    (let [ctx (get-tool-context cofx)
          {:keys [layer-event layer-type transaction-kind]} ctx]
      (cond
        (not= :vector layer-type)
        {:fx [[:warn (str "Anchor tool is invalid for " layer-type)]]}

        (not= transaction-kind (anchor-extrude-modal/kind))
        {:fx [[:warn "No active extrude transaction"]]}

        :else
        {:transaction
         [(tx/transaction-operation
            (anchor-extrude-modal/kind)
            :move
            :layer-event layer-event)]}))))

;; 退出锚点挤出模态 —— commit
(rf/reg-event-fx
  store/app-id :anchor/exit-extrude-anchor-modal
  [(cleanup-tool-interceptor AnchorState :factory (fn [_] (anchor.state/make-anchor-state)))
   (tool-context-interceptor)
   (transaction-interceptor)]
  (fn [cofx _]
    (let [ctx (get-tool-context cofx)
          {:keys [layer-type transaction-kind]} ctx]
      (cond
        (not= :vector layer-type)
        {:fx [[:warn (str "Anchor tool is invalid for " layer-type)]]}

        (not= transaction-kind (anchor-extrude-modal/kind))
        {:fx [[:warn "No active extrude transaction"]]}

        :else
        {:record (-> (:record cofx)
                     (assoc-in [:canvas-state :tool-data :mode]  nil)
                     (assoc-in [:canvas-state :tool-data :modal] false))
         :transaction [(tx/commit-transaction (anchor-extrude-modal/kind))]}))))
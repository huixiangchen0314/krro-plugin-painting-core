(ns top.kzre.krro.plugin.painting.core.edit.anchor.width-adjust-modal
  "宽度调整模态的事件入口：
     :anchor-width-adjust/enter  —— 快捷键进入
     :anchor-width-adjust/move   —— 移动
     :anchor-width-adjust/exit   —— 退出（commit）"
  (:require
   [top.kzre.krro.canvas.vector.core :as cv]
   [top.kzre.krro.core.reframe.core :as rf]
   [top.kzre.krro.core.reframe.transaction :as tx :refer [transaction-interceptor]]
   [top.kzre.krro.plugin.painting.core.edit.anchor.state :as anchor.state]
   [top.kzre.krro.plugin.painting.core.edit.common :as common]
   [top.kzre.krro.plugin.painting.core.edit.interceptors :refer
     [cleanup-tool-interceptor tool-context-interceptor]]
   [top.kzre.krro.plugin.painting.core.store :as store]
   [top.kzre.krro.plugin.painting.core.transactions.anchor-width-adjust-modal
     :as tx-width])
  (:import
   (top.kzre.krro.plugin.painting.core.edit.anchor.state AnchorState)))

;; ─── 内部：计算屏幕距离 ───

(defn- screen-distance
  [paths anchor layer-transform viewport event]
  (let [pt        (cv/anchor-point paths anchor)
        screen-pt (common/layer-point->screen pt layer-transform viewport)
        dx        (- (:x event) (:x screen-pt))
        dy        (- (:y event) (:y screen-pt))]
    (Math/hypot dx dy)))

(rf/reg-event-fx
  store/app-id :anchor/enter-adjust-width-modal
  [(cleanup-tool-interceptor AnchorState :factory (fn [_] (anchor.state/make-anchor-state)))
   (tool-context-interceptor)
   (transaction-interceptor)]
  (fn [cofx _]
    (let [ctx       (:krro.painting/tool-context cofx)
          {:keys [layer layer-type layer-transform viewport event transaction-kind]} ctx
          tool-data (get-in (:record cofx) [:canvas-state :tool-data])
          {:keys [active-anchor selected-anchors]} tool-data]
      (cond
        (not= :vector layer-type)
        {:fx [[:warn (str "Anchor tool is invalid for " layer-type)]]}

        (nil? active-anchor)
        {:fx [[:warn "No active anchor"]]}

        (some? transaction-kind)
        {:fx [[:warn "Transaction already exists!"]]}

        :else
        (let [paths    (cv/paths layer)
              distance (screen-distance paths active-anchor
                                        layer-transform viewport event)]
          {:transaction
           [(tx/begin-transaction
              (tx-width/kind)
              :active-anchor    active-anchor
              :selected-anchors selected-anchors
              :distance         distance)]})))))

(rf/reg-event-fx
  store/app-id :anchor/adjust-width
  [(cleanup-tool-interceptor AnchorState :factory (fn [_] (anchor.state/make-anchor-state)))
   (tool-context-interceptor)
   (transaction-interceptor)]
  (fn [cofx _]
    (let [ctx (:krro.painting/tool-context cofx)
          {:keys [layer layer-transform viewport event layer-type transaction-kind]} ctx
          tool-data (get-in (:record cofx) [:canvas-state :tool-data])
          {:keys [active-anchor]} tool-data]
      (cond
        (not= :vector layer-type)
        {:fx [[:warn (str "Anchor tool is invalid for " layer-type)]]}

        (not= transaction-kind (tx-width/kind))
        {:fx [[:warn "No active width-adjust transaction"]]}

        :else
        (let [paths    (cv/paths layer)
              distance (screen-distance paths active-anchor
                                        layer-transform viewport event)]
          {:transaction
           [(tx/transaction-operation
              (tx-width/kind)
              :move
              :distance distance)]})))))

(rf/reg-event-fx
  store/app-id :anchor/exit-adjust-width-modal
  [(cleanup-tool-interceptor AnchorState :factory (fn [_] (anchor.state/make-anchor-state)))
   (tool-context-interceptor)
   (transaction-interceptor)]
  (fn [cofx _]
    (let [ctx (:krro.painting/tool-context cofx)
          {:keys [layer layer-transform viewport event layer-type transaction-kind]} ctx
          tool-data (get-in (:record cofx) [:canvas-state :tool-data])
          {:keys [active-anchor]} tool-data
          {:keys [layer-type transaction-kind]} ctx]
      (cond
        (not= :vector layer-type)
        {:fx [[:warn (str "Anchor tool is invalid for " layer-type)]]}

        (not= transaction-kind (tx-width/kind))
        {:fx [[:warn "No active width-adjust transaction"]]}

        :else
        (let [paths    (cv/paths layer)
              distance (screen-distance paths active-anchor
                                        layer-transform viewport event)]
          {:transaction
           [(tx/commit-transaction
              (tx-width/kind)
              :distance distance)]})
        ))))
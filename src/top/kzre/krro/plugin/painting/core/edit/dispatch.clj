(ns top.kzre.krro.plugin.painting.core.edit.dispatch
  (:require
    [top.kzre.krro.core.reframe.core :as rf]
    [top.kzre.krro.plugin.painting.core.edit.anchor.state :as anchor.state]
    [top.kzre.krro.plugin.painting.core.edit.tool :as tool :refer [cleanup-tool-interceptor]]
    [top.kzre.krro.plugin.painting.core.store :as store]))

(defmulti tool-event
  (fn [current-tool event-map]
    (if (and (= :pointer (:device-type event-map))
             (= :middle (:mouse-button event-map)))
      :viewport
      current-tool)
    ))
;; 抽取到UI
(defmethod tool-event :default [_ _] nil)

;; 分发工具事件
(rf/reg-event-fx
  store/app-id :tool/dispatch-event
  (tool/dispatch-fn tool-event))

(rf/reg-event-fx
  store/app-id :tool/select-tool
  [(cleanup-tool-interceptor)]
  (fn [cofx [_ _ tool-id]]
    {:record
     (-> (:record cofx)
         (assoc-in [ :tool-data]
                   (case tool-id
                     ;; 默认状态
                     :anchor-translate (anchor.state/make-anchor-state)
                     nil))
         (assoc-in [ :current-tool] tool-id))}))

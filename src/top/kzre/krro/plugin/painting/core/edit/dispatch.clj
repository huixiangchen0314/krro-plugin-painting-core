(ns top.kzre.krro.plugin.painting.core.edit.dispatch
  (:require
   [top.kzre.krro.core.reframe :as rf]
   [top.kzre.krro.plugin.painting.core.edit.anchor-translate :as anchor]
   [top.kzre.krro.plugin.painting.core.edit.interceptors :refer [cleanup-tool-interceptor]]
   [top.kzre.krro.plugin.painting.core.store :as store]))

(defmulti tool-event
  (fn [current-tool event-map]
    (if (and (= :pointer (:device-type event-map))
             (= :middle (:mouse-button event-map)))
      :viewport
      current-tool)
    ))

(defmethod tool-event :default [_ _] nil)

;; 分发工具事件
(rf/reg-event-fx
  store/app-id :tool/dispatch-event
  (fn [cofx [_ record-id event-map frame]]
    (let [record (:record cofx)
          current-tool (get-in record [:canvas-state :current-tool])]
      (when-let [event-id (tool-event current-tool event-map)]
        {:dispatch [event-id record-id event-map frame]}
        ))))

(rf/reg-event-fx
  store/app-id :tool/select-tool
  [(cleanup-tool-interceptor)]
  (fn [cofx [_ _ tool-id]]
    {:record
     (-> (:record cofx)
         (assoc-in [:canvas-state :tool-data]
                   (case tool-id
                     :anchor-translate (anchor/->AnchorState #{} nil nil)
                     nil))
         (assoc-in [:canvas-state :current-tool] tool-id))}))

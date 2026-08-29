(ns top.kzre.krro.plugin.painting.core.edit.dispatch
  (:require
    [top.kzre.krro.core.reframe :as rf]
    [top.kzre.krro.plugin.painting.core.store :as store]))

(defmulti tool-event
          "转化原始输入事件为reframe工具事件id,默认为nil.
          根据 [current-tool event-type] 二元组分派."
  (fn [current-tool event-map] [current-tool (:type event-map)]))

(defmethod tool-event :default [_ _] nil)

;; 分发工具事件
(rf/reg-event-fx
  store/app-id :tool/dispatch-event
  (fn [cofx [_ record-id event-map frame]]
    (let [record (:record cofx)
          current-tool (get-in record [:canvas-state :current-tool])]
      (when-let [event-id (tool-event current-tool event-map)]
        {:record (-> record
                     (assoc-in [:canvas-state :cursor-position]
                               {:x (:x event-map)
                                :y (:y event-map)}))
         :dispatch [event-id record-id event-map frame]}))))
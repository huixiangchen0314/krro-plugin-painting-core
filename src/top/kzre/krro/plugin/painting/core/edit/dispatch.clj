(ns top.kzre.krro.plugin.painting.core.edit.dispatch
  (:require
    [top.kzre.krro.core.reframe.core :as rf]
    [top.kzre.krro.plugin.painting.core.edit.anchor.state :as anchor.state]
    [top.kzre.krro.plugin.painting.core.edit.interceptors :refer [cleanup-tool-interceptor]]
    [top.kzre.krro.plugin.painting.core.edit.protocol :as p]
    [top.kzre.krro.plugin.painting.core.record :as record]
    [top.kzre.krro.plugin.painting.core.store :as store]))

(defmulti tool-event
  (fn [current-tool event-map]
    (if (and (= :pointer (:device-type event-map))
             (= :middle (:mouse-button event-map)))
      :viewport
      current-tool)
    ))
;; 抽取到UI
;; TODO 抽取成独立范式
(defmethod tool-event :default [_ _] nil)
;; TODO 抽象提取出来，以便复用
;; 分发工具事件
(rf/reg-event-fx
  store/app-id :tool/dispatch-event
  (fn [cofx event-v]
    (let [[_ record-id event-map frame] event-v
          record (:record cofx)
          current-tool (get-in record [:canvas-state :current-tool])
          tool (record/current-tool record)]
      (when-let [event-id
                 (or
                   ;; 先尝试基于状态的分派
                   (when (and tool
                              (satisfies? p/IToolData tool))
                     (p/dispatch-event tool event-map cofx))
                   ;; 再进行基于配置的分派
                   (tool-event current-tool event-map))]
        {:dispatch
         ;; 替换事件ID，后面的参数透传
         (into [event-id] (rest event-v))}
        ))))

(rf/reg-event-fx
  store/app-id :tool/select-tool
  [(cleanup-tool-interceptor)]
  (fn [cofx [_ _ tool-id]]
    {:record
     (-> (:record cofx)
         (assoc-in [:canvas-state :tool-data]
                   (case tool-id
                     :anchor-translate (anchor.state/make-anchor-state)
                     nil))
         (assoc-in [:canvas-state :current-tool] tool-id))}))

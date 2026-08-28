(ns top.kzre.krro.plugin.painting.core.edit.core
  (:require
   [top.kzre.krro.core.reframe :as rf]
   [top.kzre.krro.plugin.painting.core.store :as store]))


(rf/reg-event-fx
  store/app-id :log-layers
  (fn [cofx [_ record-id]]                         ;; 解构事件向量
    (let [record (:record cofx)
          layers (-> record :canvas-data :layers)]
      {:record record
       :fx [[:log-info (str "Current layers of " record-id ": " layers)]]})))
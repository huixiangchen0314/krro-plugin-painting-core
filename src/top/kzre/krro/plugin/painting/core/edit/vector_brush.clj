(ns top.kzre.krro.plugin.painting.core.edit.vector-brush
  (:require [top.kzre.krro.core.reframe :as rf]
            [top.kzre.krro.plugin.painting.core.edit.interceptors :refer [cleanup-tool-interceptor]]
            [top.kzre.krro.plugin.painting.core.store :as store]))


(rf/reg-event-fx
  store/app-id :vector-brush-tool/press
  [(cleanup-tool-interceptor)]
  (fn [_ record-id event-map]
    ))

(rf/reg-event-fx
  store/app-id :vector-brush-tool/drag
  (fn [_ record-id event-map]
    ))


(rf/reg-event-fx
  store/app-id :vector-brush-tool/release
  (fn [_ record-id event-map]
    ))
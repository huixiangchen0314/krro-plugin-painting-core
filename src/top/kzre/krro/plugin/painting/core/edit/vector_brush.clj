(ns top.kzre.krro.plugin.painting.core.edit.vector-brush
  (:require [top.kzre.krro.core.reframe :as rf]
            [top.kzre.krro.plugin.painting.core.store :as store]))


(rf/reg-fx
  store/app-id :vector-brush-tool/press
  (fn [_ record-id event-map]
    ))

(rf/reg-fx
  store/app-id :vector-brush-tool/drag
  (fn [_ record-id event-map]
    ))


(rf/reg-fx
  store/app-id :vector-brush-tool/release
  (fn [_ record-id event-map]
    ))
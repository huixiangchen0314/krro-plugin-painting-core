(ns top.kzre.krro.plugin.painting.core.brush.events
  (:require
    [top.kzre.krro.core.reframe :as rf]
    [top.kzre.krro.plugin.painting.core.store :as store]))


(rf/reg-event-fx
  store/app-id :brush/set-global-brush
  (fn [cofx [_ record-id color]]
    {:fx [[:brush/set-global-brush color]]}))
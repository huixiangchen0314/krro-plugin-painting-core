(ns top.kzre.krro.plugin.painting.core.session.events
  (:require
   [top.kzre.krro.core.reframe.core :as rf]
   [top.kzre.krro.plugin.painting.core.session.store :as session.store]))


(rf/reg-event-fx
  session.store/app-id :set-snap-opts
  (fn [cofx [_ _ opts]]
    ))
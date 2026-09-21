(ns top.kzre.krro.plugin.painting.core.session.subs
  (:require [top.kzre.krro.core.reframe.core :as rf]
            [top.kzre.krro.plugin.painting.core.session.store :as session.store]))


(rf/reg-sub
  session.store/app-id
  :session-id
  :<- [:record]
  :session-id)

(rf/reg-sub
  session.store/app-id
  :snap-options
  :<- [:record]
  :snap-options)

(rf/reg-sub
  session.store/app-id
  :brush
  :<- [:record]
  (fn [session] (:brush session)))
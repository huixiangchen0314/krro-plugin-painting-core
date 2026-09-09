(ns top.kzre.krro.plugin.painting.core.brush.effects
  (:require [top.kzre.krro.core.reframe :as rf]
            [top.kzre.krro.plugin.painting.core.store :as store]))

(rf/reg-fx
  store/app-id :brush/set-global-brush
  (fn [color]
    ))
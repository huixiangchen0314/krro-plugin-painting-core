(ns top.kzre.krro.plugin.painting.core.edit.effects
  (:require
    [top.kzre.krro.core.reframe :as rf]
    [top.kzre.krro.core.util.heartbeat-flag :as hb]
    [top.kzre.krro.core.variable :as variable]
    [top.kzre.krro.plugin.painting.core.store :as store]))

(rf/reg-fx
  store/app-id :tool/set-command-enabled
  (fn [_ enabled?]
    (if enabled?
      (hb/clear! variable/command-disabled ::set-command-enabled)
      (hb/beat! variable/command-disabled ::set-command-enabled))))
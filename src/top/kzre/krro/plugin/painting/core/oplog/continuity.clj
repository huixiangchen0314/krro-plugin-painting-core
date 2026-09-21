(ns top.kzre.krro.plugin.painting.core.oplog.continuity
  (:require
    [top.kzre.krro.core.reframe.core :as rf]
    [top.kzre.krro.plugin.painting.core.store :as store]))


(rf/reg-event-fx
  store/app-id :oplog/continuity-set
  (fn [cofx [_ canvas-id layer-id path-id idx continuity & {:as ctx}]]
    ;; TODO: 调 v/set-continuity → change/make-vector-anchors-attr-changed
    ))

(rf/reg-event-fx
  store/app-id :oplog/continuity-apply
  (fn [cofx [_ canvas-id layer-id path-id & {:as ctx}]]
    ;; TODO: 调 v/apply-constraints → change/make-vector-anchors-attr-changed
    ))
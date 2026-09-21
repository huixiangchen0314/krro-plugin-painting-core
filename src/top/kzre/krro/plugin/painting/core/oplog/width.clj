(ns top.kzre.krro.plugin.painting.core.oplog.width
  (:require [top.kzre.krro.core.reframe.core :as rf]
            [top.kzre.krro.plugin.painting.core.store :as store]))


(rf/reg-event-fx
  store/app-id :oplog/width-adjust-point
  (fn [cofx [_ canvas-id layer-id path-id deltas & {:as ctx}]]
    ;; TODO: 调 v/set-anchors-width → change/make-vector-anchors-attr-changed
    ))

(rf/reg-event-fx
  store/app-id :oplog/width-adjust-t
  (fn [cofx [_ canvas-id layer-id path-id anchor-t radius deltas & {:as ctx}]]
    ;; TODO: 调 v/adjust-widths-by-t → change/make-vector-path-attr-changed
    ))
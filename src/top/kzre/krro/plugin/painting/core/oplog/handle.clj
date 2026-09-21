(ns top.kzre.krro.plugin.painting.core.oplog.handle
  (:require
    [top.kzre.krro.core.reframe.core :as rf]
    [top.kzre.krro.plugin.painting.core.store :as store]))



(rf/reg-event-fx
  store/app-id :oplog/handle-set-in
  (fn [cofx [_ canvas-id layer-id path-id idx dx dy & {:as ctx}]]
    ;; TODO: 调 v/set-handle-in → change/make-vector-anchors-attr-changed
    ))

(rf/reg-event-fx
  store/app-id :oplog/handle-set-out
  (fn [cofx [_ canvas-id layer-id path-id idx dx dy & {:as ctx}]]
    ;; TODO: 调 v/set-handle-out → change/make-vector-anchors-attr-changed
    ))

(rf/reg-event-fx
  store/app-id :oplog/handle-clear
  (fn [cofx [_ canvas-id layer-id path-id idx & {:as ctx}]]
    ;; TODO: 调 v/clear-handles → change/make-vector-anchors-attr-changed
    ))

(rf/reg-event-fx
  store/app-id :oplog/handle-mirror-in-from-out
  (fn [cofx [_ canvas-id layer-id path-id idx & {:as ctx}]]
    ;; TODO: 调 v/mirror-in-from-out → change/make-vector-anchors-attr-changed
    ))

(rf/reg-event-fx
  store/app-id :oplog/handle-mirror-out-from-in
  (fn [cofx [_ canvas-id layer-id path-id idx & {:as ctx}]]
    ;; TODO: 调 v/mirror-out-from-in → change/make-vector-anchors-attr-changed
    ))

(rf/reg-event-fx
  store/app-id :oplog/handle-move-in
  (fn [cofx [_ canvas-id layer-id path-id idx dx dy & {:as ctx}]]
    ;; TODO: 调 v/move-handle-in → change/make-vector-anchors-attr-changed
    ))

(rf/reg-event-fx
  store/app-id :oplog/handle-move-out
  (fn [cofx [_ canvas-id layer-id path-id idx dx dy & {:as ctx}]]
    ;; TODO: 调 v/move-handle-out → change/make-vector-anchors-attr-changed
    ))
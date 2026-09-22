(ns top.kzre.krro.plugin.painting.core.oplog.width
  (:require
   [top.kzre.krro.canvas.vector.core :as cv]
   [top.kzre.krro.core.reframe.core :as rf]
   [top.kzre.krro.plugin.painting.core.changes.path :as change]
   [top.kzre.krro.plugin.painting.core.record :as record]
   [top.kzre.krro.plugin.painting.core.store :as store]))

;; TODO 衰减编辑
(rf/reg-event-fx
  store/app-id :oplog/anchor-width-adjust
  (fn [cofx [_ canvas-id layer-id selected-anchors delta
             & {:keys [paths path-ids snap-opts falloff-opts pivot-opts]
                :as ctx}]]
    (let [{:keys [layer-transform layer]} (record/layer-context (:record cofx) layer-id)
          old-paths (cv/paths layer)
          base-paths (or paths (cv/paths layer))
          new-paths (cv/adjust-widths base-paths selected-anchors delta)
          op        (change/make-vector-anchors-attr-changed
                      layer-id old-paths new-paths selected-anchors
                      layer-transform)]
      {:dispatch [:oplog/log canvas-id op ctx]})))

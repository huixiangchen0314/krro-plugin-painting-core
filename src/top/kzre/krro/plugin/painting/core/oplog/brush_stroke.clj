(ns top.kzre.krro.plugin.painting.core.oplog.brush-stroke
  (:require
   [top.kzre.krro.brush.core :as brush-core]
   [top.kzre.krro.core.reframe.reframe :as rf]
   [top.kzre.krro.plugin.painting.core.changes.raster-layer :as change]
   [top.kzre.krro.plugin.painting.core.record :as record]
   [top.kzre.krro.plugin.painting.core.store :as store]))


(rf/reg-event-fx
  store/app-id :oplog/brush-stroke
  (fn [cofx [_ record-id layer-id stroke
             & {:keys [canvas] :as ctx}]]
    (let [{:keys [layer layer-transform]}
          (record/layer-context (:record cofx) layer-id)
          base-canvas (or canvas (.copy (:canvas layer)))
          [rendered-canvas dirties] (brush-core/render-stroke base-canvas stroke)
          op (change/make-raster-layer-dirty layer-id base-canvas rendered-canvas
                                             dirties layer-transform)]
      {:dispatch [:oplog/log record-id op ctx]})))



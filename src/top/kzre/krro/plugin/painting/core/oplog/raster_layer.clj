(ns top.kzre.krro.plugin.painting.core.oplog.raster-layer
  (:require [top.kzre.krro.core.reframe.core :as rf]
            [top.kzre.krro.plugin.painting.core.changes.raster-layer :as change]
            [top.kzre.krro.plugin.painting.core.record :as record]
            [top.kzre.krro.plugin.painting.core.store :as store])
  (:import (top.kzre.krro.util.tile TiledCanvas)))


(rf/reg-event-fx
  store/app-id :oplog/replace-raster-layer-canvas
  (fn [cofx [_ record-id layer-id canvas
             & {:keys [undo?]
                :or {undo? false}}]]
    (let [{:keys [layer layer-transform]}
          (record/layer-context (:record cofx) layer-id)
          layer-canvas (:canvas layer)
          dirties (into (set (.getTiles ^TiledCanvas layer-canvas))
                        (set (.getTiles ^TiledCanvas canvas)))
          op (change/make-raster-layer-dirty layer-id (.copy layer-canvas) canvas dirties layer-transform)]
      {:dispatch [:oplog/log record-id op {:undo? undo?}]})))

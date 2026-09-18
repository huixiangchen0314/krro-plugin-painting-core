(ns top.kzre.krro.plugin.painting.core.oplog.vector-layer
  (:require [top.kzre.krro.core.reframe.core :as rf]
            [top.kzre.krro.plugin.painting.core.store :as store]))

;; 更新矢量图层路径
(rf/reg-event-fx
  store/app-id :oplog/replace-vector-layer-paths
  (fn [cofx [_ record-id layer-id canvas
             & {:keys [undo?]
                :or {undo? false}}]]))
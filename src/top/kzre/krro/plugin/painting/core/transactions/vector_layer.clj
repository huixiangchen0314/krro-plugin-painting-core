(ns top.kzre.krro.plugin.painting.core.transactions.vector-layer
  (:require
    [top.kzre.krro.canvas.vector.core :as cv]
    [top.kzre.krro.plugin.painting.core.record :as record]))


(defn rollback-effect
  [record layer-backup]
  (let [{:keys [canvas-id layer-id layer]} (record/layer-context record)]
    {:dispatch [:oplog/vector-layer-paths-dirty
                canvas-id layer-id
                (cv/paths layer)
                (cv/paths layer-backup)
                :undo? false]
     :fx [[:tool/set-command-enabled true]]}))
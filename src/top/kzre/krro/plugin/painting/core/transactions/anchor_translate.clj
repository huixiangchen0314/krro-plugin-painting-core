(ns top.kzre.krro.plugin.painting.core.transactions.anchor-translate
  "锚点移动事务"
  (:require
    [top.kzre.krro.core.reframe.transaction :as tx]
    [top.kzre.krro.plugin.painting.core.algo.anchor :refer [Anchor]]
    [top.kzre.krro.plugin.painting.core.layer.clone :as clone]
    [top.kzre.krro.plugin.painting.core.project.vector-layer :as pv]
    [top.kzre.krro.plugin.painting.core.record :as record])
  (:import (top.kzre.krro.canvas.vector.anchor Anchor)))

(defonce ^:private kind* ::anchor-translate)

(defn kind [] kind*)

(defrecord AnchorTranslateTransaction
  [anchors
   layer-backup
   init-point]
  tx/ITransaction
  (kind [_] (kind))
  (begin [_ {:keys [layer-event]
             :as kwargs} record]
    (let [{:keys [layer]} (record/layer-context record)
          ]
      [(->AnchorTranslateTransaction
         (:anchors kwargs)
         (clone/clone-layer layer)
         (select-keys layer-event [:x :y]))
       {:fx [[:tool/set-command-enabled false]]}]))

  (operate [this op-kind {:keys [layer-event]} record]
    (case op-kind
      :drag
      nil
      (throw (ex-info "unknown op-kind for transaction vector-stroke" {:op-kind op-kind}))))

  (commit [_ _ record]
    )
  (rollback [_ _ record]
    (let [{:keys [canvas-id layer-id layer]}
          (record/layer-context record)]
      {:dispatch [:oplog/vector-layer-paths-dirty
                  canvas-id layer-id
                  (pv/paths layer)
                  (pv/paths layer-backup)
                  :undo? false]
       :fx       [[:tool/set-command-enabled true]]})))

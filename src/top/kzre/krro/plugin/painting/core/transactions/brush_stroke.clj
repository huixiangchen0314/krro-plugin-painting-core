(ns top.kzre.krro.plugin.painting.core.transactions.brush-stroke
  "笔刷笔触事务"
  (:require
    [top.kzre.krro.brush.core :as brush-core]
    [top.kzre.krro.core.reframe.transaction :as tx]
    [top.kzre.krro.plugin.painting.core.changes.raster-layer :as change]
    [top.kzre.krro.plugin.painting.core.layer.clone :as clone]
    [top.kzre.krro.plugin.painting.core.record :as record]
    [top.kzre.krro.plugin.painting.core.tool.stroke :as stroke])
  (:import
    (top.kzre.krro.brush Stroke)
    (top.kzre.krro.util.tile TiledCanvas)))

(defrecord BrushStrokeTransaction
  [^Stroke stroke
   layer-backup
   ^int rendered-event-count]

  tx/ITransaction
  (kind [_] :brush-stroke)

  (begin [_ _ record]
    (let [{:keys [layer]} (record/layer-context record)
          stroke (stroke/make-stroke)
          backup (clone/clone-layer layer)]
      [(->BrushStrokeTransaction stroke backup 0 )
       {:fx [[:tool/set-command-enabled false]]}]))


  (operate [this op-kind kwargs record]
    (case op-kind
      :drag
      (let [{:keys [layer-event]} kwargs
            {:keys [ layer-id layer  layer-transform]}
            (record/layer-context record)
            ^TiledCanvas layer-canvas (:canvas layer)
            pevent     (stroke/->pointer-event layer-event)
            new-stroke (.append stroke pevent)]
        (if (> (.size new-stroke) (.size stroke))
          (let [last-count (- (.size new-stroke) rendered-event-count)
                stroke'    (.last new-stroke last-count)
                [new-canvas dirties] (brush-core/render-stroke layer-canvas stroke')
                new-t       (-> this
                                (assoc :stroke new-stroke)
                                (update :rendered-event-count + last-count))]
            [new-t
             {:dispatch [:change
                         (change/make-raster-layer-dirty
                          layer-id nil new-canvas dirties layer-transform)]}])
          ;; 本帧无新事件
          [this {:fx []}]))
      [this {:fx []}]))

  (commit [_ _kwargs record]
    (if (and stroke (> (.size stroke) 0))
      (let [{:keys [ layer-id layer-transform]}
            (record/layer-context record)
            backup-canvas (:canvas layer-backup)
            [new-canvas dirties] (brush-core/render-stroke backup-canvas stroke)
            ]
        {:dispatch [:change
                    (change/make-raster-layer-dirty
                     layer-id backup-canvas new-canvas dirties layer-transform)
                    {:undo true}]
         :fx     [[:tool/set-command-enabled true]]})
      {:fx [[:tool/set-command-enabled true]]}))

  (rollback [_ _ctx record]
    (let [{:keys [layer-id layer  layer-transform]} (record/layer-context record)
          backup-canvas (:canvas layer-backup)]
      {:dispatch [:change
                  (change/make-raster-layer-dirty
                   layer-id (:canvas layer) backup-canvas nil layer-transform)]
       :fx [[:tool/set-command-enabled true]]})))

(tx/reg-transaction :brush-stroke (->BrushStrokeTransaction nil nil 0))
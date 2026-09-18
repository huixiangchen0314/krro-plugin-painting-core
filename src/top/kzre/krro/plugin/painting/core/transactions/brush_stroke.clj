(ns top.kzre.krro.plugin.painting.core.transactions.brush-stroke
  "笔刷笔触事务"
  (:require
    [top.kzre.krro.core.reframe.transaction :as tx]
    [top.kzre.krro.plugin.painting.core.layer.clone :as clone]
    [top.kzre.krro.plugin.painting.core.record :as record]
    [top.kzre.krro.plugin.painting.core.tool.stroke :as stroke])
  (:import
    (top.kzre.krro.brush Stroke)))

(defonce ^:private transaction-kind* ::brush-stroke)

(defn kind [] transaction-kind*)

(defrecord BrushStrokeTransaction
  [^Stroke stroke
   layer-backup
   ^int rendered-event-count]

  tx/ITransaction
  (kind [_] (kind))

  (begin [_ _ record]
    (let [{:keys [layer]} (record/layer-context record)
          stroke (stroke/make-stroke)
          backup (clone/clone-layer layer)]
      [(->BrushStrokeTransaction stroke backup 0)
       {:fx [[:tool/set-command-enabled false]]}]))

  (operate [this op-kind kwargs record]
    (case op-kind
      :drag
      (let [{:keys [layer-event]} kwargs
            {:keys [canvas-id layer-id]} (record/layer-context record)
            pevent     (stroke/->pointer-event layer-event)
            new-stroke (.append stroke pevent)]
        (if (> (.size new-stroke) (.size stroke))
          (let [last-count (- (.size new-stroke) rendered-event-count)
                stroke'    (.last new-stroke last-count)
                new-t      (-> this
                               (assoc :stroke new-stroke)
                               (update :rendered-event-count + last-count))]
            [new-t
             {:dispatch [:oplog/brush-stroke canvas-id layer-id stroke'
                         :undo? false]}])
          [this {:fx []}]))
      [this {:fx []}]))

  (commit [_ _kwargs record]
    (if (and stroke (> (.size stroke) 0))
      (let [{:keys [canvas-id layer-id]} (record/layer-context record)
            canvas-backup (:canvas layer-backup)]
        {:dispatch [:oplog/brush-stroke canvas-id layer-id stroke
                    :canvas canvas-backup
                    :undo? true]
         :fx [[:tool/set-command-enabled true]]})
      {:fx [[:tool/set-command-enabled true]]}))

  (rollback [_ _ctx record]
    (let [{:keys [canvas-id layer-id]} (record/layer-context record)
          canvas-backup (:canvas layer-backup)]
      {:dispatch [:oplog/replace-raster-layer-canvas canvas-id layer-id canvas-backup
                  :undo? false]
       :fx [[:tool/set-command-enabled true]]})))

(tx/reg-transaction (kind) (->BrushStrokeTransaction nil nil 0))
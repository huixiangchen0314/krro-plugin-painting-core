(ns top.kzre.krro.plugin.painting.core.transactions.vector-stroke
  "矢量笔触事务"
  (:import (top.kzre.krro.brush Stroke))
  (:require
    [top.kzre.krro.core.reframe.transaction :as tx]
    [taoensso.timbre :as log]
    [top.kzre.krro.plugin.painting.core.layer.clone :as clone]
    [top.kzre.krro.plugin.painting.core.record :as record]
    [top.kzre.krro.plugin.painting.core.tool.stroke :as stroke]))

(defonce ^:private transaction-kind* ::vector-stroke)
(defn kind [] transaction-kind*)


(defrecord VectorStrokeTransaction
  [^Stroke stroke
   layer-backup]
  tx/ITransaction
  (kind [_] (kind))
  (begin [this kwargs record]
    (let [{:keys [layer]} (record/layer-context record)
          stroke (stroke/make-stroke)
          backup (clone/clone-layer layer)]
      [(->VectorStrokeTransaction stroke backup)
       {:fx [[:tool/set-command-enabled false]]}]))
  (operate [this op-kind kwargs record] this)
  (commit [this  kwargs record]
    nil)
  (rollback [this _ _]

    nil))

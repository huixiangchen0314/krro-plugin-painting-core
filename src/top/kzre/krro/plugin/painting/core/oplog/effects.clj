(ns top.kzre.krro.plugin.painting.core.oplog.effects
  (:require
   [top.kzre.krro.core.reframe.core :as rf]
   [top.kzre.krro.plugin.painting.core.oplog.protocol :as proto]
   [top.kzre.krro.plugin.painting.core.record :as record]
   [top.kzre.krro.plugin.painting.core.store :as store])
  (:import
    (java.lang AutoCloseable)
    (top.kzre.krro.plugin.painting.core.oplog.protocol IOperation)))

(rf/reg-fx
  store/app-id :oplog/record
  (fn [_ record-id ^IOperation op]
    (try
      (let [record (record/record record-id)]
        (proto/record! op record))
      (finally
        (when (instance? AutoCloseable op)
          (.close ^AutoCloseable op))))))
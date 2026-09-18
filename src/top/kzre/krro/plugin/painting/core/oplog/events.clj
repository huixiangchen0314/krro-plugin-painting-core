(ns top.kzre.krro.plugin.painting.core.oplog.events
  (:require [top.kzre.krro.core.reframe.core :as rf]
            [top.kzre.krro.plugin.painting.core.oplog.protocol :as proto]
            [top.kzre.krro.plugin.painting.core.store :as store])
  (:import (java.lang AutoCloseable)
           (top.kzre.krro.plugin.painting.core.oplog.protocol IOperation)))


(rf/reg-event-fx
  store/app-id :oplog/log
  (fn [{:keys [record]}
       [_ record-id ^IOperation op
        {:keys [undo?]
         :or {undo? false}
         :as ctx}]]
    (try
      (let [[new-record fx] (proto/realize op record ctx)]
        (if undo?
          {:record new-record
           :fx     (conj (or fx []) [:oplog/record record-id op])}
          (do
            (when (instance? AutoCloseable op)
              (.close ^AutoCloseable op))
            {:record new-record
             :fx     fx})))
      (catch Throwable e
        (when (instance? AutoCloseable op)
          (.close ^AutoCloseable op))
        (throw e)))))
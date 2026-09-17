(ns top.kzre.krro.plugin.painting.core.oplog.oplog)


(defmulti apply-action!
          "应用动作，返回 changes"
          (fn [action _record]
            (:type action)))

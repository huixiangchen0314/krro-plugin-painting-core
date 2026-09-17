(ns top.kzre.krro.plugin.painting.core.transaction.core
  "操作日志，用于审计和调度分析, 生成变更和changes"
  (:require
   [top.kzre.krro.plugin.painting.core.transaction.transaction :as transaction]))

(def transaction-interceptor transaction/transaction-interceptor)

(ns top.kzre.krro.plugin.painting.core.oplog.protocol)

(defprotocol IOperation
  (realize [_ record] "纯：应用本次变更，返回新 record'")
  (record! [_ record] "副作用：记录本次变更，在 realize 之后调用"))
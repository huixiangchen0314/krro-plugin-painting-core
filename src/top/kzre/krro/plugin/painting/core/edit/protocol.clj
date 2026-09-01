(ns top.kzre.krro.plugin.painting.core.edit.protocol)

(defprotocol IToolData
  (cleanup! [_] "清理工具状态")
  (overlay [_ ctx] "返回叠加层描述"))

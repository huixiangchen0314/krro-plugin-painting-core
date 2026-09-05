(ns top.kzre.krro.plugin.painting.core.edit.protocol)

(defprotocol IToolData
  (cleanup! [_ ctx] "清理工具状态")
  (overlay [_ ctx] "返回叠加层描述")
  (dispatch-event [_ event-map] "根据当前状态进行事件分派，避免单个reframe事件逻辑复杂，nil表示返回使用配置分派")
  (target-layers [_] "当前工具有效应用的图层类型，nil表示所有图层"))

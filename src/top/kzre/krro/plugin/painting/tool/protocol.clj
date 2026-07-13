(ns top.kzre.krro.plugin.painting.tool.protocol)

(defprotocol ITool
 (begin! [this canvas-id] "用户选择此工具进行操作.")
 (end! [this canvas-id] "用户取消使用此工具.")
 (apply! [this canvas-id event] "工具负责消费事件."))

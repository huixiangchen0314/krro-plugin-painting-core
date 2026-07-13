(ns top.kzre.krro.plugin.painting.tool.protocol
  "画布工具协议：工具通过返回值表达操作意图，调度器负责实现。
   ToolContext 仅提供环境信息，不包含备份数组。
   apply! 返回 [new-layer, action-key]。"
  (:require [top.kzre.krro.plugin.painting.project.canvas]
            [top.kzre.krro.plugin.painting.canvas.state])
  (:import (top.kzre.krro.plugin.painting.canvas.state CanvasRuntime)
           (top.kzre.krro.plugin.painting.project.canvas CanvasData)))

(defrecord ToolContext
  [canvas-id
   ^CanvasData data
   ^CanvasRuntime runtime])



(defprotocol ITool
  (begin! [this layer ^ToolContext ctx]
    "工具激活时调用。")
  (end! [this layer ^ToolContext ctx]
    "工具停用时调用。")
  (apply! [this layer event ^ToolContext ctx]
    "处理单个指针事件。返回 tool-action")
  (preview! [this layer ^ToolContext ctx]
    "由动画循环调用，执行一帧预览渲染。返回 new-layer")
  (commit! [this layer ^ToolContext ctx]))
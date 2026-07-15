(ns top.kzre.krro.plugin.painting.tool.protocol
  "画布工具协议：工具通过返回值表达操作意图，调度器负责实现。
   ToolContext 仅提供环境信息，不包含备份数组。
   apply! 返回 [new-layer, action-key]。"
  (:require [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
            [top.kzre.krro.plugin.painting.canvas.state :as state])
  (:import (top.kzre.krro.plugin.painting.canvas.state CanvasRuntime)
           (top.kzre.krro.plugin.painting.core.project.canvas CanvasData)))

(defrecord ToolContext
  [canvas-id
   frame
   ^CanvasData data
   ^CanvasRuntime runtime])

(defn make-context
  "构造 ToolContext。
   支持两种调用方式：
     1. (make-context canvas-id frame)
        从全局状态查询最新的 data 和 runtime。
     2. (make-context canvas-id frame data runtime)
        直接使用给定的 data 和 runtime，避免重复查询。"
  ([canvas-id frame]
   (make-context canvas-id frame
                 (pc/canvas-data! canvas-id)
                 (state/canvas-runtime canvas-id)))
  ([canvas-id frame ^CanvasData data ^CanvasRuntime runtime]
   (->ToolContext canvas-id frame data runtime)))

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
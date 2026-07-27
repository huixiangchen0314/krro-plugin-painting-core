(ns top.kzre.krro.plugin.painting.core.core
  (:require [top.kzre.krro.core.plugin :as plugin]
            [top.kzre.krro.plugin.painting.core.project.core]
            [top.kzre.krro.plugin.painting.core.ops.undo :as undo]
            [top.kzre.krro.plugin.painting.core.canvas.core]
            [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
            [top.kzre.krro.plugin.painting.core.project.raster-layer :as pr]))

;; 文件夹结构,
;; core 命名空间下放顶级定义，核心抽象和协议约定
;; project 下统一放置项目数据定义
;; tool 完成所有工具开发
;; layer/brush 各自完成 事件和操作处理


(defn init []
  (plugin/register-plugin! pc/canvas-codec-plugin-def)
  (plugin/register-plugin! pr/tiled-canvas-codec-plugin-def)
  (undo/init-undo-hooks!)
  )


(plugin/register-plugin! {:name :krro.plugin/painting :init init})
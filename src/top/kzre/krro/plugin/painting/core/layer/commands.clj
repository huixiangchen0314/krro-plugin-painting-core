(ns top.kzre.krro.plugin.painting.core.layer.commands
  "命令注册"
  (:require [top.kzre.krro.core.command :as cmd]
            [top.kzre.krro.core.frame :as frame]
            [top.kzre.krro.core.message :as msg]
            [top.kzre.krro.core.reframe :as rf]
            [top.kzre.krro.core.window :as win]
            [top.kzre.krro.plugin.painting.core.spec :as spec]))


(defn new-raster-layer-command
  "从当前 frame 获取 canvas-id，并切换当前图层为 layer-id。"
  [_]
  (if-let [f (win/active-frame)]
    (if-let [canvas-id (frame/param f spec/canvas-id-key)]
      (rf/dispatch :krro.painting [:new-raster-layer canvas-id])
      (msg/warn "No canvas-id found in current frame"))
    (msg/warn "No current frame active")))

(cmd/reg-command
  :krro.painting/new-raster-layer
  new-raster-layer-command
  :description "创建空白光栅图层"
  :interactive true)
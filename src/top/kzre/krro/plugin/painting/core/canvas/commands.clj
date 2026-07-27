(ns top.kzre.krro.plugin.painting.core.canvas.commands
  (:require
    [top.kzre.krro.core.command :as cmd]
    [top.kzre.krro.core.frame :as frame]
    [top.kzre.krro.core.message :as msg]
    [top.kzre.krro.core.reframe :as rf]
    [top.kzre.krro.plugin.painting.core.spec :as spec]))

(defn log-layers-command
  "从当前 frame 获取 canvas-id 并记录其图层信息。"
  [_project]
  (if-let [f frame/*current-frame*]
    (if-let [canvas-id (frame/param f spec/canvas-id-key)]
      (rf/dispatch :krro.painting [:log-layers canvas-id])
      (msg/warn "No canvas-id found in current frame"))
    (msg/warn "No current frame active")))

(cmd/reg-command
  :krro.painting/log-layers
  log-layers-command
  :description "输出当前画布的图层信息")


(defn select-layer-command
  "从当前 frame 获取 canvas-id，并切换当前图层为 layer-id。"
  [_project layer-id]
  (if-let [f frame/*current-frame*]
    (if-let [canvas-id (frame/param f spec/canvas-id-key)]
      (rf/dispatch :krro.painting [:select-layer canvas-id layer-id])
      (msg/warn "No canvas-id found in current frame"))
    (msg/warn "No current frame active")))

(cmd/reg-command
  :krro.painting/select-layer
  select-layer-command
  :description "切换当前绘画图层"
  :interactive [:keyword])   ;; 可选：提示输入 layer-id
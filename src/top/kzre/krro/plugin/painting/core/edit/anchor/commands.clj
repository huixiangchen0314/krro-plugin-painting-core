(ns top.kzre.krro.plugin.painting.core.edit.anchor.commands
  "立即操作命令绑定"
  (:require
    [top.kzre.krro.core.command :as cmd]
    [top.kzre.krro.core.frame :as frame]
    [top.kzre.krro.core.message :as msg]
    [top.kzre.krro.core.reframe.core :as rf]
    [top.kzre.krro.plugin.painting.core.spec :as spec]
    [top.kzre.krro.plugin.painting.core.store :as store]
    [top.kzre.krro.plugin.painting.core.record :as record]
    [taoensso.timbre :as log]
    [top.kzre.krro.core.core :as kcc]))


;; 进入宽度调整模块
(cmd/reg-command
  :krro.painting.anchor/enter-adjust-width-modal
  (fn [_]
    (if-let [frame (kcc/active-frame)]
      (if-let [canvas-id (frame/param frame spec/canvas-id-key)]
        (if-let [cursor-position (record/cursor-position (record/record canvas-id))]
          (rf/dispatch store/app-id [:anchor/enter-adjust-width-modal canvas-id cursor-position frame])
          (log/warn "cursor-position is nil"))
        (msg/warn "No canvas-id found in current frame"))
      (msg/warn "No current frame active")))
  :description "Enter width adjustment modal for selected anchors (S key)"
  :interactive true)

;; 进入挤出锚点模态
(cmd/reg-command
  :krro.painting.anchor/enter-extrude-anchor-modal
  (fn [_]
    (if-let [frame (kcc/active-frame)]
      (if-let [canvas-id (frame/param frame spec/canvas-id-key)]
        (if-let [cursor-position (record/cursor-position (record/record canvas-id))]
          (rf/dispatch store/app-id [:anchor/enter-extrude-anchor-modal canvas-id cursor-position frame])
          (msg/warn "cursor-position is nil"))
        (msg/warn "No canvas-id found in current frame"))
      (msg/warn "No current frame active")))
  :description "Enter extrude anchor modal (default E key)"
  :interactive true)
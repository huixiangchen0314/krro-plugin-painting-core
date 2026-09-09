(ns top.kzre.krro.plugin.painting.core.edit.commands
  (:require
    [top.kzre.krro.core.command :as cmd]
    [top.kzre.krro.core.frame :as frame]
    [top.kzre.krro.core.message :as msg]
    [top.kzre.krro.core.reframe :as rf]
    [top.kzre.krro.plugin.painting.core.spec :as spec]
    [top.kzre.krro.plugin.painting.core.store :as store]
    [top.kzre.krro.plugin.painting.core.record :as record]
    [taoensso.timbre :as log]
    [top.kzre.krro.core.core :as krro]))

(cmd/reg-command
  :krro.painting.anchor/enter-adjust-width-modal
  (fn [_]
    (if-let [f (krro/active-frame)]
      (if-let [canvas-id (frame/param f spec/canvas-id-key)]
        (if-let [cursor-position (record/cursor-position canvas-id)]
          (rf/dispatch store/app-id [:anchor/enter-adjust-width-modal canvas-id cursor-position f])
          (log/warn "cursor-position is nil"))
        (msg/warn "No canvas-id found in current frame"))
      (msg/warn "No current frame active")))
  :description "Enter width adjustment modal for selected anchors (S key)"
  :interactive true)

(cmd/reg-command
  :krro.painting.anchor/enter-extrude-anchor-modal
  (fn [_]
    (if-let [f (krro/active-frame)]
      (if-let [canvas-id (frame/param f spec/canvas-id-key)]
        (if-let [cursor-position (record/cursor-position canvas-id)]
          (rf/dispatch store/app-id [:anchor/enter-extrude-anchor-modal canvas-id cursor-position f])
          (msg/warn "cursor-position is nil"))
        (msg/warn "No canvas-id found in current frame"))
      (msg/warn "No current frame active")))
  :description "Enter extrude anchor modal (default E key)"
  :interactive true)
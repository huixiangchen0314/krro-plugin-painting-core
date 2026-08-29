(ns top.kzre.krro.plugin.painting.core.undo.edit-canvas
  (:require
    [top.kzre.krro.core.hook :as hook]
    [top.kzre.krro.plugin.painting.core.ops.undo :as core]
    [top.kzre.krro.plugin.painting.core.spec :as spec]
    [top.kzre.krro.plugin.painting.core.state :as state]
    [top.kzre.krro.plugin.undo.core :as undo]))

;; TODO 计算dirty-tiles
(defn record-canvas-edited! [canvas-id]
  (undo/record-state!
    {:type              ::edit-canvas
     :seq               (core/inc-undo-metadata-seq-key)
     :canvas-id         canvas-id}))

;; TODO  dispatch 回 reframe 处理
(defmethod core/restore-canvas-state! [:after-undo ::edit-canvas]
  [_ {:keys [canvas-id]}]
  (state/invalidate-canvas-dirty! canvas-id)
  (hook/run-hook! spec/layer-changed-hook-key canvas-id))

(defmethod core/restore-canvas-state! [:after-redo ::edit-canvas]
  [_ {:keys [canvas-id]}]
  (state/invalidate-canvas-dirty! canvas-id)
  (hook/run-hook! spec/layer-changed-hook-key canvas-id))
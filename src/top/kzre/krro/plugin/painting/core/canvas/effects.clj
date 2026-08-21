(ns top.kzre.krro.plugin.painting.core.canvas.effects
  "画布相关的副作用处理器"
  (:require [taoensso.timbre :as log]
            [top.kzre.krro.canvas.core.layer.core :as lc]
            [top.kzre.krro.core.hook :as hook]
            [top.kzre.krro.core.reframe :as rf]
            [top.kzre.krro.plugin.painting.core.ops.backup :as backup]
            [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
            [top.kzre.krro.plugin.painting.core.state :as state]))

(rf/reg-fx
  :krro.painting :log-info
  (fn [_app-id message]
    (log/info message)))


(rf/reg-fx
  :krro.painting :switch-layer-backup-fx
  (fn [_app-id record-id new-layer-id]
    (when-let [state (state/canvas-runtime record-id)]
      ;; 无需更新 state, 仅释放图层数据
      (backup/release-backup! state)
      ;; 备份新图层（必须基于最新的 runtime 状态）
      (when new-layer-id
        (when-let [new-layer (lc/find-layer new-layer-id (pc/layers-by-id! record-id))]
          (let [current (state/canvas-runtime record-id)]
            (when-let [new-st (backup/backup-layer! new-layer current)]
              (swap! state/canvas-runtimes assoc record-id new-st))))))))


(rf/reg-fx
  :krro.painting :set-selected-layer-fx
  (fn [_app-id record-id layer-id]
    (swap! state/canvas-runtimes assoc-in [record-id :selected-layer-id] layer-id)
    (swap! state/canvas-runtimes assoc-in [record-id :selected-layer-ids] [layer-id])))

(rf/reg-fx
  :krro.painting :render-canvas-fx
  (fn [_app-id record-id]
    ;; 1. 更新预览画布的像素数据（纯数据操作，无 UI 依赖）
    (state/render-canvas! record-id)
    ;; 2. 通过 hook 通知外部：预览画布已刷新，各视图可自行更新
    (hook/run-hook! :krro.painting/after-render-canvas-hook record-id)))

(rf/reg-fx
  :krro.painting :rerender-canvas-frame-fx
  (fn [_app-id record-id]
    (state/rerender-frame-with-canvas-id! record-id)))

(rf/reg-fx
  :krro.painting :select-multi-layer-fx
  (fn [_app-id record-id layer-id]
    (when (state/canvas-runtime record-id)
      (swap! state/canvas-runtimes assoc-in [record-id :selected-layer-id] layer-id)
      (swap! state/canvas-runtimes update-in [record-id :selected-layer-ids]
             (fn [ids]
               (vec (distinct (conj (or ids []) layer-id))))))))


(rf/reg-fx
  :krro.painting :add-dirty-tiles
  (fn [_ canvas-id tiles]
    (state/add-dirty-tiles! canvas-id tiles)))

(ns top.kzre.krro.plugin.painting.core.canvas.effects
  "画布相关的副作用处理器"
  (:require
   [clojure.core.async :as async]
   [taoensso.timbre :as log]
   [top.kzre.krro.canvas.core.layer.core :as lc]
   [top.kzre.krro.core.reframe :as rf]
   [top.kzre.krro.plugin.painting.core.layer.clone :as clone]
   [top.kzre.krro.plugin.painting.core.layer.destroy :as destroy]
   [top.kzre.krro.plugin.painting.core.layer.dispose :as dispose]
   [top.kzre.krro.plugin.painting.core.ops.backup :as backup]
   [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
   [top.kzre.krro.plugin.painting.core.project.raster-layer :as pr]
   [top.kzre.krro.plugin.painting.core.render :as render]
   [top.kzre.krro.plugin.painting.core.state :as state]
   [top.kzre.krro.plugin.painting.core.store :as store]))



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
  :krro.painting :render-canvas
  (fn [_ record-id dirty-tiles]
    {:pre [(not (nil? dirty-tiles))]}
    (let [cd (pc/canvas-data! record-id)
          state (state/canvas-runtime record-id)
          ch (render/get-render-chan record-id)]
      (when-not (empty? dirty-tiles)
        (async/put! ch {:canvas-id record-id
                        :layers (mapv clone/clone-layer (:layers cd))
                        :width (:width cd)
                        :height (:height cd)
                        :dirty-tiles dirty-tiles
                        :canvas (:preview-canvas state)})))))


(rf/reg-fx
  store/app-id :close-canvas
  (fn [_ record-id]
    ;; 释放画布数据
    (let [cd (pc/canvas-data! record-id)
          layers (:layers cd)]
      (doseq [l layers]
        (destroy/destroy-layer l)))
    ;; 关闭画布渲染通道
    (render/close-render-chan record-id)))


(rf/reg-fx
  :krro.painting :rerender-canvas-frame-fx
  (fn [_ record-id]
    (state/rerender-frame-with-canvas-id! record-id)))

(ns top.kzre.krro.plugin.painting.core.canvas.effects
  "画布相关的副作用处理器"
  (:require
   [clojure.core.async :as async]
   [taoensso.timbre :as log]
   [top.kzre.krro.core.reframe :as rf]
   [top.kzre.krro.plugin.painting.core.layer.clone :as clone]
   [top.kzre.krro.plugin.painting.core.layer.destroy :as destroy]
   [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
   [top.kzre.krro.plugin.painting.core.render :as render]
   [top.kzre.krro.plugin.painting.core.state :as state]
   [top.kzre.krro.plugin.painting.core.store :as store]))

(rf/reg-fx
  :krro.painting :log-info
  (fn [_app-id message]
    (log/info message)))

(rf/reg-fx
  :krro.painting :render-canvas
  (fn [_ record-id dirty-tiles]
    {:pre [(not (nil? dirty-tiles))]}
    (let [cd (pc/canvas-data! record-id)
          layers (:layers cd)
          width (:width cd)
          height (:height cd)
          state (state/canvas-runtime record-id)
          canvas (:preview-canvas state)]
      (render/request-render! record-id layers width height canvas dirty-tiles))))


(rf/reg-fx
  store/app-id :close-canvas
  (fn [_ record-id]
    ;; 释放画布数据
    (let [cd (pc/canvas-data! record-id)
          layers (:layers cd)]
      (doseq [l layers]
        (destroy/destroy-layer l)))
    ;; 关闭画布渲染通道
    (render/shutdown-render! record-id)))


(rf/reg-fx
  :krro.painting :rerender-canvas-frame-fx
  (fn [_ record-id]
    (state/rerender-frame-with-canvas-id! record-id)))

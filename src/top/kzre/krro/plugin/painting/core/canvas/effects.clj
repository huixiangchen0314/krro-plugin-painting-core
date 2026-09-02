(ns top.kzre.krro.plugin.painting.core.canvas.effects
  "画布相关的副作用处理器"
  (:require
   [taoensso.timbre :as log]
   [top.kzre.krro.core.hook :as hook]
   [top.kzre.krro.core.message :as msg]
   [top.kzre.krro.core.reframe :as rf]
   [top.kzre.krro.plugin.painting.core.layer.destroy :as destroy]
   [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
   [top.kzre.krro.plugin.painting.core.state :as state]
   [top.kzre.krro.plugin.painting.core.store :as store]
   ))

(rf/reg-fx
  :krro.painting :log-info
  (fn [_app-id message]
    (log/info message)))

(rf/reg-fx
  store/app-id :warn
  (fn [_ message]
    (msg/warn message)))

(rf/reg-fx
  store/app-id :message
  (fn [_ message]
    (msg/message message)))

(rf/reg-fx
  store/app-id :error
  (fn [_ message]
    (msg/error message)))



(rf/reg-fx
  :krro.painting :render-canvas
  (fn [_ record-id dirty-tiles transform]
    (let [cd (pc/canvas-data! record-id)]
      (hook/run-hook! :krro.painting/render-canvas-hook record-id cd dirty-tiles transform))))


(rf/reg-fx
  store/app-id :close-canvas
  (fn [_ record-id]
    ;; 释放画布数据
    (let [cd (pc/canvas-data! record-id)
          layers (:layers cd)]
      (doseq [l layers]
        (destroy/destroy-layer l))
      (store/unreg-canvas-store record-id))))


(rf/reg-fx
  :krro.painting :rerender-canvas-frame-fx
  (fn [_ record-id]
    (state/rerender-frame-with-canvas-id! record-id)))

(ns top.kzre.krro.plugin.painting.core.canvas.subs
  "画布相关的反应式订阅定义"
  (:require [top.kzre.krro.core.reframe :as rf]
            [top.kzre.krro.plugin.painting.core.store :as store]))

(rf/reg-sub
  store/app-id
  :canvas-data
  :<- [:record]
  :canvas-data)                              ; keyword 作为提取函数


(rf/reg-sub
  store/app-id
  :layers
  :<- [:canvas-data]                         ; 依赖上层订阅
  :layers)

(rf/reg-sub
  store/app-id
  :current-layer-id
  :<- [:canvas-data]
  :current-layer-id)

(rf/reg-sub
  store/app-id
  :current-tool
  (fn [record]
    (get-in record [:record :canvas-state :current-tool])))
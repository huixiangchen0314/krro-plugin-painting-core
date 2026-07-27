(ns top.kzre.krro.plugin.painting.core.canvas.subs
  "画布相关的反应式订阅定义"
  (:require [top.kzre.krro.core.reframe :as rf]))

(rf/reg-sub
  :krro.painting
  :canvas-data
  :<- [:record]
  :canvas-data)                              ; keyword 作为提取函数


(rf/reg-sub
  :krro.painting
  :layers
  :<- [:canvas-data]                         ; 依赖上层订阅
  :layers)

(rf/reg-sub
  :krro.painting
  :current-layer-id
  :<- [:canvas-data]
  :current-layer-id)
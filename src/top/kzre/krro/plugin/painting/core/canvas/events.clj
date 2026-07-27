(ns top.kzre.krro.plugin.painting.core.canvas.events
  "画布相关的事件处理器"
  (:require [top.kzre.krro.core.reframe :as rf]))

(rf/reg-event-fx
  :krro.painting :log-layers
  (fn [cofx [_ record-id]]                         ;; 解构事件向量
    (let [record (:record cofx)
          layers (-> record :canvas-data :layers)]
      {:record record
       :fx [[:log-info (str "Current layers of " record-id ": " layers)]]})))


(rf/reg-event-fx
  :krro.painting :select-layer
  (fn [cofx [_ record-id layer-id]]
    (let [cd (:canvas-data (:record cofx))]
      (if (= (:current-layer-id cd) layer-id)
        {:record (:record cofx)}
        {:record (assoc-in (:record cofx) [:canvas-data :current-layer-id] layer-id)
         :fx [[:switch-layer-backup-fx record-id layer-id]  ;; 备份切换
              [:set-selected-layer-fx record-id layer-id]   ;; 运行时选中
              [:render-canvas-fx record-id]                 ;; 核心重绘
              [:rerender-canvas-frame-fx record-id]]}))))   ;; UI 刷新


(rf/reg-event-fx
  :krro.painting :multi-select-layer
  (fn [cofx [_ record-id layer-id]]
    {:record (:record cofx)
     :fx [[:select-multi-layer-fx record-id layer-id]]}))

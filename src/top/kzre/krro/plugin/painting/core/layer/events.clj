(ns top.kzre.krro.plugin.painting.core.layer.events
  "事件注册（业务逻辑）"
  (:require
    [top.kzre.krro.canvas.raster.core :as raster]
    [top.kzre.krro.core.reframe :as rf]
    [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
    [top.kzre.krro.canvas.core.layer.util :as lu]))

;; 新建空白光栅图层
(rf/reg-event-fx
  :krro.painting :new-raster-layer
  (fn [cofx [_ record-id]]                         ;; 解构事件向量
    (let [selected-id (pc/current-layer-id record-id)       ;; 从项目数据获取
          layers      (pc/layers-by-id! record-id)
          path        (lu/above-layer-path selected-id layers)
          new-layer (raster/make-raster-layer pc/global-tile-size)
          layer-id (:id new-layer)]
      {:record (assoc-in (:record cofx) [:canvas-data :current-layer-id] layer-id)
       :fx
       [[:insert-layer-fx record-id path new-layer]             ;; 插入新图层
        [:save-raster-data-fx record-id layer-id]              ;; 创建光栅数据侧表
        [:switch-layer-backup-fx record-id layer-id]            ;; 备份图层数据
        [:set-selected-layer-fx record-id layer-id]             ;; 选中新图层
        [:record-raster-layer-added record-id layer-id]  ;; 新增光栅图层 undo 状态记录
        [:rerender-canvas-frame-fx record-id]           ;; UI 刷新
        [:log-info (str "new-raster-layer " record-id ": " layers)]]})))

;; 新建空白矢量图层
(rf/reg-event-fx
  :krro.painting :new-vector-layer
  (fn [cofx [_ record-id]]                         ;; 解构事件向量
    (let [record (:record cofx)
          layers (-> record :canvas-data :layers)]
      {:record record
       :fx [[:log-info (str "Current layers of " record-id ": " layers)]]})))

;; 新建空白透视图层
(rf/reg-event-fx
  :krro.painting :new-perspective-layer
  (fn [cofx [_ record-id]]                         ;; 解构事件向量
    (let [record (:record cofx)
          layers (-> record :canvas-data :layers)]
      {:record record
       :fx [[:log-info (str "Current layers of " record-id ": " layers)]]})))


;; 移除光栅图层
(rf/reg-event-fx
  :krro.painting :remove-raster-layer
  (fn [cofx [_ record-id]]                         ;; 解构事件向量
    (let [record (:record cofx)
          layers (-> record :canvas-data :layers)]
      {:record record
       :fx [[:log-info (str "Current layers of " record-id ": " layers)]]})))

(rf/reg-event-fx
  :krro.painting :remove-vector-layer
  (fn [cofx [_ record-id]]                         ;; 解构事件向量
    (let [record (:record cofx)
          layers (-> record :canvas-data :layers)]
      {:record record
       :fx [[:log-info (str "Current layers of " record-id ": " layers)]]})))

(rf/reg-event-fx
  :krro.painting :remove-perspective-layer
  (fn [cofx [_ record-id]]                         ;; 解构事件向量
    (let [record (:record cofx)
          layers (-> record :canvas-data :layers)]
      {:record record
       :fx [[:log-info (str "Current layers of " record-id ": " layers)]]})))


(rf/reg-event-fx
  :krro.painting :move-layer
  (fn [cofx [_ record-id]]                         ;; 解构事件向量
    (let [record (:record cofx)
          layers (-> record :canvas-data :layers)]
      {:record record
       :fx [[:log-info (str "Current layers of " record-id ": " layers)]]})))

(rf/reg-event-fx
  :krro.painting :duplicate-raster-layer
  (fn [cofx [_ record-id from-path to-path]]                         ;; 解构事件向量
    (let [record (:record cofx)
          layers (-> record :canvas-data :layers)]
      {:record record
       :fx [[:log-info (str "Current layers of " record-id ": " layers)]]})))

(rf/reg-event-fx
  :krro.painting :duplicate-vector-layer
  (fn [cofx [_ record-id]]                         ;; 解构事件向量
    (let [record (:record cofx)
          layers (-> record :canvas-data :layers)]
      {:record record
       :fx [[:log-info (str "Current layers of " record-id ": " layers)]]})))


(rf/reg-event-fx
  :krro.painting :duplicate-perspective-layer
  (fn [cofx [_ record-id]]                         ;; 解构事件向量
    (let [record (:record cofx)
          layers (-> record :canvas-data :layers)]
      {:record record
       :fx [[:log-info (str "Current layers of " record-id ": " layers)]]})))



(rf/reg-event-fx
  :krro.painting :set-layer-visibility
  (fn [cofx [_ record-id]]                         ;; 解构事件向量
    (let [record (:record cofx)
          layers (-> record :canvas-data :layers)]
      {:record record
       :fx [[:log-info (str "Current layers of " record-id ": " layers)]]})))
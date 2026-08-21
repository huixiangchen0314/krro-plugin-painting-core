(ns top.kzre.krro.plugin.painting.core.layer.events
  "事件注册（业务逻辑）"
  (:require
   [top.kzre.krro.canvas.core.layer.util :as lu]
   [top.kzre.krro.canvas.perspective.core :as perspective]
   [top.kzre.krro.canvas.raster.core :as raster]
   [top.kzre.krro.canvas.vector.core :as vector]
   [top.kzre.krro.core.reframe :as rf]
   [top.kzre.krro.plugin.painting.core.project.canvas :as pc]))

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
    (let [selected-id (pc/current-layer-id record-id)       ;; 从项目数据获取
          layers      (pc/layers-by-id! record-id)
          path        (lu/above-layer-path selected-id layers)
          new-layer   (vector/make-vector-layer)
          layer-id (:id new-layer)]
      {:record (assoc-in (:record cofx) [:canvas-data :current-layer-id] layer-id)
       :fx
       [[:insert-layer-fx record-id path new-layer]
        [:switch-layer-backup-fx record-id layer-id]
        [:set-selected-layer-fx record-id layer-id]
        [:record-canvas record-id]
        [:rerender-canvas-frame-fx record-id]
        [:log-info (str "new-vector-layer " record-id ": " layers)]]})))

(rf/reg-event-fx
  :krro.painting :new-perspective-layer
  (fn [cofx [_ record-id]]                         ;; 解构事件向量
    (let [selected-id (pc/current-layer-id record-id)       ;; 从项目数据获取
          layers      (pc/layers-by-id! record-id)
          path        (lu/above-layer-path selected-id layers)
          new-layer   (perspective/make-perspective-layer)
          layer-id (:id new-layer)]
      {:record (assoc-in (:record cofx) [:canvas-data :current-layer-id] layer-id)
       :fx
       [[:insert-layer-fx record-id path new-layer]
        [:switch-layer-backup-fx record-id layer-id]
        [:set-selected-layer-fx record-id layer-id]
        [:record-canvas record-id]
        [:rerender-canvas-frame-fx record-id]
        [:log-info (str "new-vector-layer " record-id ": " layers)]]})))

(rf/reg-event-fx
  :krro.painting :after-undo-add-raster-layer
  (fn [_ [_ record-id layer-id dirty-tiles]]
    {:fx [[:delete-raster-data-fx layer-id]
          [:add-dirty-tiles record-id dirty-tiles]
          [:render-canvas-fx record-id]
          [:rerender-canvas-frame-fx record-id]
          ]}))

(rf/reg-event-fx
  :krro.painting :before-redo-add-raster-layer
  (fn [_ [_ layer-id canvas-id dirty-tiles]]
    {:fx [[:unchecked-create-raster-data layer-id canvas-id dirty-tiles]]}))

(rf/reg-event-fx
  :krro.painting :after-redo-add-raster-layer
  (fn [_ [_ record-id dirty-tiles]]
    {:fx [[:add-dirty-tiles record-id dirty-tiles]
          [:render-canvas-fx record-id]
          [:rerender-canvas-frame-fx record-id]
          ]}))

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
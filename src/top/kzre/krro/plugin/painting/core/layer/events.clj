(ns top.kzre.krro.plugin.painting.core.layer.events
  "事件注册（业务逻辑）"
  (:require
    [top.kzre.krro.canvas.core.layer.util :as lu]
    [top.kzre.krro.canvas.perspective.core :as perspective]
    [top.kzre.krro.canvas.raster.core :as raster]
    [top.kzre.krro.canvas.vector.core :as vector]
    [top.kzre.krro.core.reframe :as rf]
    [top.kzre.krro.plugin.painting.core.canvas.events :as events]
    [top.kzre.krro.plugin.painting.core.layer.util :as util]
    [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
    [top.kzre.krro.plugin.painting.core.state :as state]
    [top.kzre.krro.plugin.painting.core.store :as store]))

(defn insert-layer-at
  "在 record 的 canvas-data 中按指定路径插入图层。
   返回更新后的 record。"
  [record path layer]
  (update record :canvas-data util/insert-layer-at path layer))

(defn add-dirty-tiles
  "向 record 的 canvas-state 中追加脏 tile 集合（去重）。"
  [record tiles]
  (update-in record [:canvas-state :dirty-tiles] (fn [existing] (into (or existing #{}) tiles))))

;; 新建空白光栅图层
(rf/reg-event-fx
  :krro.painting :new-raster-layer
  (fn [cofx [_ record-id]]
    (let [record (:record cofx)
          canvas-data (:canvas-data record)
          selected-id (get-in record [:canvas-data :current-layer-id])
          layers (get canvas-data :layers)
          path (lu/above-layer-path selected-id layers)
          new-layer (raster/make-raster-layer pc/global-tile-size)
          layer-id (:id new-layer)]
      {:record
       (-> record
           (insert-layer-at path new-layer)
           (events/set-current-layer layer-id)
           (events/set-selected-layer layer-id))
       :fx [[:save-raster-data-fx record-id layer-id]        ;; 创建光栅数据（I/O）
            [:record-raster-layer-added record-id layer-id]  ;; undo/redo 记录
            [:rerender-canvas-frame-fx record-id]            ;; UI 刷新
            ]})))

(rf/reg-event-fx
  :krro.painting :new-vector-layer
  (fn [cofx [_ record-id]]
    (let [record (:record cofx)
          selected-id (get-in record [:canvas-data :current-layer-id])
          layers      (get-in record [:canvas-data :layers])
          path        (lu/above-layer-path selected-id layers)
          new-layer   (vector/make-vector-layer)
          layer-id (:id new-layer)]
      {:record
       (-> record
           (insert-layer-at path new-layer)
           (events/set-current-layer layer-id)
           (events/set-selected-layer layer-id))
       :fx
       [[:record-canvas-edited record-id]
        [:rerender-canvas-frame-fx record-id]]})))

(rf/reg-event-fx
  :krro.painting :new-perspective-layer
  (fn [cofx [_ record-id]]
    (let [record (:record cofx)
          canvas-data (:canvas-data record)
          selected-id (get-in record [:canvas-data :current-layer-id])
          layers (get canvas-data :layers)
          path (lu/above-layer-path selected-id layers)
          new-layer (perspective/make-perspective-layer)
          layer-id (:id new-layer)
          new-record (-> record
                         (insert-layer-at path new-layer)
                         (events/set-current-layer layer-id)
                         (events/set-selected-layer layer-id))]
      {:record new-record
       :fx [[:record-perspective-layer-added record-id layer-id]  ;; undo 记录
            [:rerender-canvas-frame-fx record-id]]})))        ;; UI 刷新

(rf/reg-event-fx
  store/app-id :after-undo-add-raster-layer
  (fn [_ [_ record-id layer-id dirty-tiles]]
    {:fx [[:delete-raster-data-fx layer-id]       ;; 删除光栅数据（I/O）
          [:render-canvas record-id dirty-tiles]           ;; 重绘画布
          [:rerender-canvas-frame-fx record-id]]}))  ;; 刷新 UI

(rf/reg-event-fx
  :krro.painting :before-redo-add-raster-layer
  (fn [_ [_ layer-id canvas-id dirty-tiles]]
    {:fx [[:unchecked-create-raster-data layer-id canvas-id dirty-tiles]]}))

(rf/reg-event-fx
  :krro.painting :after-redo-add-raster-layer
  (fn [_ [_ record-id dirty-tiles]]
    {:fx [[:render-canvas record-id dirty-tiles]
          [:rerender-canvas-frame-fx record-id]]}))


;; 移除被选中的图层
(rf/reg-event-fx
  :krro.painting :delete-selected-layers
  (fn [cofx [_ record-id]]
    (let [st (state/canvas-runtime record-id)
          id (:selected-layer-id st)
          ids (:selected-layer-ids st)
          cd (-> cofx :record :canvas-data)
          layer-id nil
          dirty-tiles #{}]
      {:record (assoc-in (:record cofx) [:canvas-data :current-layer-id] layer-id)
       :fx [[:set-selected-layer record-id layer-id]   ;; 运行时选中
            [:render-canvas record-id dirty-tiles]                 ;; 画布重绘
            [:rerender-canvas-frame-fx record-id]         ;; UI 刷新
            ]})))



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
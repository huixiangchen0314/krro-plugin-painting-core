(ns top.kzre.krro.plugin.painting.core.canvas.events
  "画布相关的事件处理器"
  (:require [top.kzre.krro.canvas.core.layer.util :as util]
            [top.kzre.krro.core.reframe :as rf]
            [top.kzre.krro.plugin.painting.core.ops.backup :as backup]
            [top.kzre.krro.plugin.painting.core.store :as store]))

(defn set-current-layer
  [record layer-id]
  (assoc-in record [:canvas-data :current-layer-id] layer-id))

(defn set-selected-layer
  [record layer-id]
  (-> record
      (assoc-in [:canvas-state :selected-layer-id] layer-id)
      (assoc-in [:canvas-state :selected-layer-ids] [layer-id])))

(defn append-selected-layer
  "向 record 的选中图层列表追加一个图层 ID（去重），并更新当前选中 ID 为该图层。"
  [record layer-id]
  (-> record
      (update-in [:canvas-state :selected-layer-ids]
                 (fn [ids]
                   (-> (or ids [])
                       (conj layer-id)
                       (distinct)
                       (vec))))
      (assoc-in [:canvas-state :selected-layer-id] layer-id)))

(defn switch-layer-backup
  "更新图层备份数据"
  [record layer-id]
  (let [state (:canvas-state record)
        layers (get-in record [:canvas-data :layers])
        new-layer (util/find-layer layer-id layers)
        new-state (backup/backup-layer! new-layer state)]
    (backup/release-backup! state)
    (assoc record :canvas-state new-state)))

(defn clear-dirty-tiles
  [record]
  (assoc-in record [:canvas-state :dirty-tiles] #{}))

;; 日志
(rf/reg-event-fx
  :krro.painting :log-layers
  (fn [cofx [_ record-id]]                         ;; 解构事件向量
    (let [record (:record cofx)
          layers (-> record :canvas-data :layers)]
      {:record record
       :fx [[:log-info (str "Current layers of " record-id ": " layers)]]})))

;; 单选
(rf/reg-event-fx
  :krro.painting :select-layer
  (fn [cofx [_ record-id layer-id]]
    (let [cd (:canvas-data (:record cofx))]
      (if (= (:current-layer-id cd) layer-id)
        {:record (:record cofx)}
        (let [state (:canvas-state record-id)
              dirty-tiles (:dirty-tiles state)]
          {:record (-> (:record cofx)
                       (set-current-layer layer-id)
                       (set-selected-layer layer-id)
                       (switch-layer-backup layer-id)
                       (clear-dirty-tiles))
           :fx [[:render-canvas record-id dirty-tiles]      ;; 画布重绘
                [:rerender-canvas-frame-fx record-id]         ;; UI 刷新
                ]})))))

;; 多选
(rf/reg-event-fx
  :krro.painting :multi-select-layer
  (fn [cofx [_ record-id layer-id]]
    (let [new-record (append-selected-layer (:record cofx) layer-id)]
      {:record new-record
       :fx [[:rerender-canvas-frame-fx record-id]]})))


(rf/reg-event-fx
  store/app-id :close-canvas
  (fn [cofx [_ record-id]]
    {:fx [:close-canvas-render-channel record-id]}))
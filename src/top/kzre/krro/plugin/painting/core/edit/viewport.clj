(ns top.kzre.krro.plugin.painting.core.edit.viewport
  (:require
    [top.kzre.krro.core.custom :as custom]
    [top.kzre.krro.core.reframe :as rf]
    [top.kzre.krro.plugin.painting.core.edit.common :as common]
    [top.kzre.krro.plugin.painting.core.edit.interceptors :refer [cleanup-tool-interceptor]]
    [top.kzre.krro.plugin.painting.core.store :as store]
    [top.kzre.krro.plugin.painting.core.viewport :as vp])
  (:import (top.kzre.krro.plugin.painting.core.edit.protocol IToolData)))

(custom/defcustom :krro.painting/viewport-pan-speed
                  1.0
                  :type :number
                  :group :krro.painting/edit
                  :doc "视口平移速度，1.0 为像素同比速度。")

(custom/defcustom :krro.painting/viewport-pan-dead-zone
                  0.5
                  :type :number
                  :group :krro.painting/edit
                  :doc "视口平移死区（像素），位移小于此值时忽略移动。")

(custom/defcustom :krro.painting/viewport-zoom-sensitivity
                  1.005
                  :type :number
                  :group :krro.painting/edit
                  :doc "滚轮缩放灵敏度，表示每一格 delta 的缩放因子。1.1 表示每格放大 10%。")

(defrecord ViewportState [init-cursor-x init-cursor-y original-viewport moving?]
  IToolData
  (cleanup [_]
    nil)
  (overlay [_]
    nil))

;; 设置视口
(rf/reg-fx
  store/app-id :set-viewport
  (fn [_ frame viewport]
    (vp/set-viewport! frame viewport)))

(rf/reg-event-fx
  store/app-id :viewport-tool/press
  [(cleanup-tool-interceptor)]
  (fn [cofx [_ _ event-map frame]]
    (let [original-viewport (vp/get-viewport frame)
          cursor-x (:x event-map)
          cursor-y (:y event-map)
          record (:record cofx)]
      {:record (assoc-in record [:canvas-state :tool-data]
                         (->ViewportState cursor-x cursor-y original-viewport true))})))


(rf/reg-event-fx
  store/app-id :viewport-tool/drag
  (fn [cofx [_ record-id event-map frame]]
    (let [record (:record cofx)
          state (get-in record [:canvas-state :tool-data])]
      (when (instance? ViewportState state)
        (let [speed (custom/get-custom :krro.painting/viewport-pan-speed frame)
              dead-zone (custom/get-custom :krro.painting/viewport-pan-dead-zone frame)
              cursor-x (:x event-map)
              cursor-y (:y event-map)
              init-cursor-x (get-in record [:canvas-state :tool-data :init-cursor-x])
              init-cursor-y (get-in record [:canvas-state :tool-data :init-cursor-y])
              dx (* speed (- cursor-x init-cursor-x))
              dy (* speed (- cursor-y init-cursor-y))]
          (if (and (< (Math/abs (double dx)) dead-zone)
                   (< (Math/abs (double dy)) dead-zone))
            nil
            (let [original-viewport (get-in record [:canvas-state :tool-data :original-viewport])
                  zoom (:zoom original-viewport)]
              {:fx [[:set-viewport frame
                     (-> original-viewport
                         (update-in [:offset-x] (fn [offset-x] (- offset-x (/ dx zoom))))
                         (update-in [:offset-y] (fn [offset-y] (- offset-y (/ dy zoom)))))]
                    [:render-canvas record-id nil nil]]}))))
     )))


(rf/reg-event-fx
  store/app-id :viewport-tool/scroll
  (fn [cofx [_ record-id event-map frame]]
    (when-let [delta-y (:delta-y event-map)]
      (let [record (:record cofx)
            sensitivity (custom/get-custom :krro.painting/viewport-zoom-sensitivity frame)
            cursor-x (:x event-map)
            cursor-y (:y event-map)
            vp (vp/get-viewport frame)
            old-zoom (:zoom vp)
            new-zoom (-> old-zoom
                         (* (Math/pow sensitivity (double delta-y)))
                         (max 0.01) (min 100.0))
            ;; 计算鼠标指向的逻辑坐标
            lx (+ (/ cursor-x old-zoom) (:offset-x vp))
            ly (+ (/ cursor-y old-zoom) (:offset-y vp))
            ;; 新视口左上角应为 lx - sx / new-zoom
            new-offset-x (- lx (/ cursor-x new-zoom))
            new-offset-y (- ly (/ cursor-y new-zoom))
            new-viewport (assoc vp
                           :zoom new-zoom
                           :offset-x new-offset-x
                           :offset-y new-offset-y)]
        {:record
         ;; 如果是移动中缩放，就立马更新初始数据
         (when-let [state (get-in record [:canvas-state :tool-data])]
           (when (and (instance? ViewportState state)
                      (:moving? state))
             (update-in record [:canvas-state :tool-data]
                        merge
                        {:init-cursor-x cursor-x
                         :init-cursor-y cursor-y
                         :original-viewport new-viewport})))
         :fx
         [[:set-viewport frame
           (assoc vp
             :zoom new-zoom
             :offset-x new-offset-x
             :offset-y new-offset-y)]
          [:render-canvas record-id nil nil]]}))))

(rf/reg-event-fx
  store/app-id :viewport-tool/release
  (fn [cofx [_ _ _ _]]
    (let [record (:record cofx)
          state (get-in record [:canvas-state :tool-data])]
      (when (instance? ViewportState state)
        {:record  (common/cleanup-tool-data! record)}))))

(ns top.kzre.krro.plugin.painting.core.edit.viewport
  (:require
    [top.kzre.krro.core.custom :as custom]
    [top.kzre.krro.core.reframe :as rf]
    [top.kzre.krro.plugin.painting.core.edit.protocol :as p]
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
  (overlay [_ _]
    nil))

;; 设置视口
(rf/reg-fx
  store/app-id :set-viewport
  (fn [_ frame viewport]
    (vp/set-viewport! frame viewport)))

(rf/reg-event-fx
  store/app-id :viewport-tool/press
  (fn [cofx [_ _ {:keys [x y]} frame]]
    (let [original-viewport (vp/get-viewport frame)
          record (:record cofx)]
      {:record (assoc-in record [:canvas-state :viewport-state]
                         (->ViewportState x y original-viewport true))})))


(rf/reg-event-fx
  store/app-id :viewport-tool/drag
  (fn [cofx [_ record-id {:keys [x y] :as event-map} frame]]
    (let [record (:record cofx)
          state (get-in record [:canvas-state :viewport-state])]
      (when (instance? ViewportState state)
        (let [speed (custom/get-custom :krro.painting/viewport-pan-speed frame)
              dead-zone (custom/get-custom :krro.painting/viewport-pan-dead-zone frame)
              init-cursor-x (get-in record [:canvas-state :viewport-state :init-cursor-x])
              init-cursor-y (get-in record [:canvas-state :viewport-state :init-cursor-y])
              dx (* speed (- x init-cursor-x))
              dy (* speed (- y init-cursor-y))]
          (if (and (< (Math/abs (double dx)) dead-zone)
                   (< (Math/abs (double dy)) dead-zone))
            nil
            (let [tool-data (get-in record [:canvas-state :tool-data])
                  original-viewport (get-in record [:canvas-state :viewport-state :original-viewport])
                  zoom (:zoom original-viewport)
                  new-viewport (-> original-viewport
                                   (update-in [:offset-x] (fn [offset-x] (- offset-x (/ dx zoom))))
                                   (update-in [:offset-y] (fn [offset-y] (- offset-y (/ dy zoom)))))]
              {:fx [[:set-viewport frame new-viewport]
                    [:tool/flush-overlay
                     (when (and tool-data (satisfies? p/IToolData tool-data))
                       (p/overlay tool-data
                                  {:viewport new-viewport
                                   :event event-map}))
                     frame]
                    [:render-canvas record-id nil nil]]})))))))


(rf/reg-event-fx
  store/app-id :viewport-tool/scroll
  (fn [cofx [_ record-id {:keys [x y delta-y] :as event-map} frame]]
    (when delta-y
      (let [record (:record cofx)
            sensitivity (custom/get-custom :krro.painting/viewport-zoom-sensitivity frame)

            vp (vp/get-viewport frame)
            old-zoom (:zoom vp)
            new-zoom (-> old-zoom
                         (* (Math/pow sensitivity (double delta-y)))
                         (max 0.01) (min 100.0))
            ;; 计算鼠标指向的逻辑坐标
            lx (+ (/ x old-zoom) (:offset-x vp))
            ly (+ (/ y old-zoom) (:offset-y vp))
            ;; 新视口左上角应为 lx - sx / new-zoom
            new-offset-x (- lx (/ x new-zoom))
            new-offset-y (- ly (/ y new-zoom))
            new-viewport (assoc vp
                           :zoom new-zoom
                           :offset-x new-offset-x
                           :offset-y new-offset-y)
            tool-data (get-in record [:canvas-state :tool-data])]
        {:record
         ;; 如果是移动中缩放，就立马更新初始数据
         (when-let [state (get-in record [:canvas-state :viewport-state])]
           (when (and (instance? ViewportState state)
                      (:moving? state))
             (update-in record [:canvas-state :viewport-state]
                        merge
                        {:init-cursor-x x
                         :init-cursor-y y
                         :original-viewport new-viewport})))
         :fx
         [[:set-viewport frame new-viewport]
          [:tool/flush-overlay
           (when (and tool-data (satisfies? p/IToolData tool-data))
             (p/overlay tool-data
                        {:viewport new-viewport
                         :event event-map}))
           frame]
          [:render-canvas record-id nil nil]]}))))

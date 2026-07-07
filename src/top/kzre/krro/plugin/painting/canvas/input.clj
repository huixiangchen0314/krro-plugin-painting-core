(ns top.kzre.krro.plugin.painting.canvas.input
  "输入源抽象：将硬件事件（鼠标、触摸、数位板）转换为统一的画笔事件。
   每个输入源提供 (start! canvas-node runtime-state) 函数，
   返回清理函数 (stop!) 用于解绑。"
  (:require [top.kzre.krro.plugin.painting.canvas.state :as state])
  (:import (javafx.event EventHandler)))

(defn- ->event-map
  "将原始坐标转换为内部事件 map。未来可根据输入源类型扩展（压力、倾斜等）。"
  [x y & {:keys [pressure velocity]
          :or   {pressure 0.8 velocity 0.5}}]
  {:x          x
   :y          y
   :pressure   pressure
   :velocity   velocity
   :timestamp  (System/currentTimeMillis)})

;; ── 鼠标输入源 ────────────────────────────────────
(defn make-mouse-input []
  (let [pressed? (atom false)]
    {:start!
     (fn [canvas runtime-state {:keys [on-stroke-start on-stroke-end]}]
       (let [on-press (reify EventHandler
                        (handle [_ e]
                          (reset! pressed? true)
                          (state/begin-stroke! runtime-state)
                          (when on-stroke-start (on-stroke-start))
                          (state/push-event! runtime-state
                                             (->event-map (.getX e) (.getY e)))))
             on-drag  (reify EventHandler
                        (handle [_ e]
                          (when @pressed?
                            (state/push-event! runtime-state
                                               (->event-map (.getX e) (.getY e))))))
             on-release (reify EventHandler
                          (handle [_ e]
                            (when @pressed?
                              (state/push-event! runtime-state
                                                 (->event-map (.getX e) (.getY e)))
                              (reset! pressed? false)
                              (when on-stroke-end (on-stroke-end)))))]
         (.setOnMousePressed canvas on-press)
         (.setOnMouseDragged canvas on-drag)
         (.setOnMouseReleased canvas on-release)
         (fn stop! []
           (.setOnMousePressed canvas nil)
           (.setOnMouseDragged canvas nil)
           (.setOnMouseReleased canvas nil))))}))
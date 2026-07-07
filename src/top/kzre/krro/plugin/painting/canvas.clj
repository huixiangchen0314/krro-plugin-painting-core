(ns top.kzre.krro.plugin.painting.canvas
  "基于 JavaFX 的离屏画布，集成 krro-brush 新 API。
   使用 AnimationTimer 定时累积事件，保证重采样与插值效果。
   增加性能统计日志。"
  (:require [taoensso.timbre :as log]
            [top.kzre.krro.brush.core :as brush]
            [top.kzre.krro.plugin.painting.canvas.project :as canvas-proj])
  (:import (java.util UUID)
           (javafx.animation AnimationTimer)
           (javafx.application Platform)
           (javafx.event EventHandler)
           (javafx.scene.canvas Canvas)
           (javafx.scene.image PixelFormat)
           (top.kzre.colorutils.color RGB)))

;; ── 笔刷定义（颜色为 float[] RGBA）─────────────────
(def simple-brush
  {:dab        {:type :circle :mask-type :hard :radius 8.0}
   :color      (RGB/rgba (float 0.0) (float 0.0) (float 1.0) (float 1.0))  ;; 蓝色
   :dynamics   {:pressure {:radius {:curve :linear :min 10.0 :max 50.0}}}
   :spacing    0.2
   :radius     8.0
   :blend-mode :normal
   :mix-mode   :default
   :taper-start 0.0
   :taper-end 0.0})

;; ── 工具：float 数组转预乘 int 数组 ─────────────
(defn- convert-to-int-array [^floats src w h]
  (let [len (* w h)
        dst (int-array len)]
    (dotimes [y h]
      (dotimes [x w]
        (let [idx   (* 4 (+ x (* y w)))
              r     (aget src idx)
              g     (aget src (inc idx))
              b     (aget src (+ idx 2))
              a     (aget src (+ idx 3))
              a-int (max 0 (min 255 (int (* a 255))))
              pre-r (max 0 (min 255 (int (* r a 255))))
              pre-g (max 0 (min 255 (int (* g a 255))))
              pre-b (max 0 (min 255 (int (* b a 255))))
              pixel (unchecked-int (bit-or (bit-shift-left a-int 24)
                                           (bit-shift-left pre-r 16)
                                           (bit-shift-left pre-g 8)
                                           pre-b))]
          (aset dst (+ x (* y w)) pixel))))
    dst))

(defn create-canvas [props]
  (let [frame-counter  (atom 0)
        canvas-id      (:krro.painting/canvas-id props (str (UUID/randomUUID)))
        default-canvas-width   (int (or (:krro.painting/canvas-width props) 800))
        default-canvas-height  (int (or (:krro.painting/canvas-height props) 600))
        ;; 从项目原子获取或创建画布像素缓冲区
        canvas-data (canvas-proj/polyfill-canvas-data canvas-id default-canvas-width default-canvas-height)
        canvas-width (.width canvas-data)
        canvas-height (.height canvas-data)
        history-data  (.data canvas-data)
        fx-canvas      (Canvas. (double canvas-width) (double canvas-height))
        pending-events (atom [])
        last-handle-time (atom nil)

        upload (fn [data]
                 (let [t0 (System/currentTimeMillis)
                       pixels (convert-to-int-array data default-canvas-width default-canvas-height)
                       t1 (System/currentTimeMillis)]
                   (Platform/runLater
                     (fn []
                       (let [pw (.getPixelWriter (.getGraphicsContext2D fx-canvas))]
                         (.setPixels pw 0 0 default-canvas-width default-canvas-height
                                     (PixelFormat/getIntArgbPreInstance)
                                     pixels 0 default-canvas-width)
                         (log/debugf "Upload: convert=%dms" (- t1 t0)))))))

        ;; 预览定时器
        timer (proxy [AnimationTimer] []
                (handle [now]
                  (let [t-start (System/currentTimeMillis)
                        events  @pending-events]
                    (when (seq events)
                      (try
                        (let [preview-data (aclone history-data)
                              stroke       (brush/events->stroke simple-brush
                                                                 events
                                                                 (:spacing simple-brush)
                                                                 (:radius simple-brush))
                              param-count  (count (:params stroke))
                              t-stroke     (System/currentTimeMillis)
                              dirty-rect   (brush/render-stroke! preview-data default-canvas-width default-canvas-height stroke)
                              t-render     (System/currentTimeMillis)]
                          (upload preview-data)
                          (when-let [prev @last-handle-time]
                            (log/debugf "Frame: events=%d stroke-pts=%d gen=%dms render=%dms dirty=%s interval=%dms"
                                        (count events) param-count
                                        (- t-stroke t-start) (- t-render t-stroke)
                                        dirty-rect
                                        (- t-start prev)))
                          (reset! last-handle-time t-start))
                        (catch Exception e
                          (log/error e "Preview error")))))))

        ;; 释放时提交
        commit-final (fn []
                       (.stop timer)
                       (let [t-commit-start (System/currentTimeMillis)
                             events (first (swap-vals! pending-events (constantly [])))]
                         (when (seq events)
                           (try
                             (let [stroke      (brush/events->stroke simple-brush
                                                                     events
                                                                     (:spacing simple-brush)
                                                                     (:radius simple-brush))
                                   param-count (count (:params stroke))
                                   _           (brush/render-stroke! history-data default-canvas-width default-canvas-height stroke)
                                   t-after     (System/currentTimeMillis)]
                               (upload history-data)
                               (log/infof "Stroke committed: events=%d stroke-pts=%d commit-time=%dms"
                                          (count events) param-count
                                          (- t-after t-commit-start)))
                             (catch Exception e
                               (log/error e "Commit error"))))))]

    ;; 鼠标事件绑定（不变）
    (.setOnMousePressed fx-canvas
                        (reify EventHandler
                          (handle [_ event]
                            (reset! pending-events [])
                            (swap! pending-events conj
                                   {:x (.getX event) :y (.getY event)
                                    :pressure 0.8 :velocity 0.5
                                    :timestamp (System/currentTimeMillis)})
                            (.start timer)
                            (reset! last-handle-time nil)
                            (log/debugf "Mouse pressed at (%.1f, %.1f)" (.getX event) (.getY event)))))

    (.setOnMouseDragged fx-canvas
                        (reify EventHandler
                          (handle [_ event]
                            (swap! pending-events conj
                                   {:x (.getX event) :y (.getY event)
                                    :pressure 0.8 :velocity 0.5
                                    :timestamp (System/currentTimeMillis)}))))

    (.setOnMouseReleased fx-canvas
                         (reify EventHandler
                           (handle [_ event]
                             (swap! pending-events conj
                                    {:x (.getX event) :y (.getY event)
                                     :pressure 0.8 :velocity 0.5
                                     :timestamp (System/currentTimeMillis)})
                             (commit-final))))

    ;; 节点仍直接返回，但画布数据已托管在项目原子中
    fx-canvas))
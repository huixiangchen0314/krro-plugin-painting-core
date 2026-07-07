(ns top.kzre.krro.plugin.painting.canvas.loop
  "渲染循环：使用完整笔划事件序列生成笔触，预览增量，提交固化为持久化。"
  (:require [taoensso.timbre :as log]
            [top.kzre.krro.plugin.painting.canvas.state :as state]
            [top.kzre.krro.plugin.painting.canvas.brush :as brush]
            [top.kzre.krro.brush.core :as brush-core])
  (:import [javafx.animation AnimationTimer]))

(defn make-loop [runtime upload-fn]
  (let [[w h] (state/canvas-size runtime)
        persistent (state/persistent-pixels runtime)
        preview    (state/preview-buffer runtime)
        stroke     (state/stroke-buffer runtime)
        get-brush  #(or (state/current-brush runtime) brush/default-brush)

        render-with-events (fn []
                             (let [events (state/get-events runtime)]
                               (when (seq events)
                                 ;; 1. 拷贝笔画累积缓冲区到预览缓冲
                                 (System/arraycopy stroke 0 preview 0 (alength stroke))
                                 ;; 2. 使用完整事件序列生成笔触并绘制到预览缓冲
                                 (let [b      (get-brush)
                                       stroke-obj (brush-core/events->stroke b events (:spacing b) (:radius b))]
                                   (brush-core/render-stroke! preview w h stroke-obj))
                                 ;; 3. 将预览结果写回笔画累积缓冲区（保留到下一帧）
                                 (System/arraycopy preview 0 stroke 0 (alength preview))
                                 ;; 4. 上传预览缓冲
                                 (upload-fn preview w h))))

        timer (proxy [AnimationTimer] []
                (handle [_]
                  (try
                    (render-with-events)
                    (catch Exception e
                      (log/error e "Render loop error")))))

        commit-fn (fn []
                    ;; 最终渲染：使用最终的事件序列绘制到笔画累积缓冲区
                    (let [events (state/get-events runtime)]
                      (when (seq events)
                        (let [b (get-brush)
                              stroke-obj (brush-core/events->stroke b events (:spacing b) (:radius b))]
                          (brush-core/render-stroke! stroke w h stroke-obj))
                        ;; 将笔画累积缓冲区拷贝回持久化数据
                        (System/arraycopy stroke 0 persistent 0 (alength stroke))
                        ;; 上传最终结果
                        (upload-fn persistent w h))
                      ;; 清空事件序列（笔画结束）
                      (state/begin-stroke! runtime)))]  ;; 重新初始化（为下一个笔画准备）
    {:timer  timer
     :commit commit-fn}))
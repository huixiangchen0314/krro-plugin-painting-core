(ns top.kzre.krro.plugin.painting.canvas.loop
  (:require [taoensso.timbre :as log]
            [top.kzre.krro.plugin.painting.canvas.state :as state]
            [top.kzre.krro.plugin.painting.canvas.brush :as brush]
            [top.kzre.krro.plugin.painting.canvas.layer :as layer]
            [top.kzre.krro.brush.core :as brush-core]
            [top.kzre.krro.brush.taper :as taper]
            [top.kzre.krro.canvas.core.canvas.protocol :as cp])
  (:import [javafx.animation AnimationTimer]))

(defn make-loop [runtime upload-fn]
  (layer/auto-select-layer! runtime)
  (let [cd          (state/get-canvas-data runtime)
        w           (long (.width cd))
        h           (long (.height cd))
        get-brush   #(or (state/current-brush runtime) brush/default-brush)
        preview     (state/preview-buffer runtime)
        layer-buf   (state/layer-buffer runtime)          ;; 图层原始数据备份
        target-pixels (fn [] (when-let [l (layer/get-selected-layer runtime)]
                               (cp/data (:canvas l))))

        render-preview (fn []
                         (let [new-evs (state/drain-new-events runtime)]
                           (when (seq new-evs)
                             (let [b      (get-brush)
                                   src (target-pixels)
                                   stroke (brush-core/events->stroke b new-evs
                                                                     (:spacing b) (:radius b))
                                   global-end (state/get-stroke-length runtime)
                                   tapered (taper/taper-stroke-start stroke (:taper-start b)
                                                                     :fields [:radius :opacity]
                                                                     :end-dist global-end)]
                               (brush-core/render-stroke! src w h tapered) ;; 直接渲染在原图层
                               (layer/render-canvas! runtime preview) ;; 渲染到preview
                               (upload-fn preview w h)))))

        timer (proxy [AnimationTimer] []
                (handle [_] (try (render-preview) (catch Exception e (log/error e "Render error")))))

        commit-fn (fn []
                    (let [all-evs (state/get-all-events runtime)
                          b       (get-brush)
                          dest    (target-pixels)]
                      (when (and (seq all-evs) dest)
                        ;; 在图层原始数据备份上绘制完整锥化笔触
                        (let [stroke  (brush-core/events->stroke b all-evs (:spacing b) (:radius b))
                              global-end (state/get-stroke-length runtime)
                              tapered (taper/taper-stroke stroke (:taper-start b) (:taper-end b)
                                                          :fields [:radius :opacity]
                                                          :end-dist global-end)]
                          (brush-core/render-stroke! layer-buf w h tapered)
                          ;; 将结果写回真实图层
                          (System/arraycopy layer-buf 0 dest 0 (alength layer-buf))
                          (layer/render-canvas! runtime preview) ;; 渲染到preview
                          (upload-fn preview w h)))))]
    {:timer timer :commit commit-fn}))
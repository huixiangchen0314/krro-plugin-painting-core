(ns top.kzre.krro.plugin.painting.canvas.loop
  (:require
   [taoensso.timbre :as log]
   [top.kzre.krro.brush.core :as brush-core]
   [top.kzre.krro.brush.taper :as taper]
   [top.kzre.krro.canvas.core.canvas.protocol :as cp]
  [top.kzre.krro.canvas.core.floats-pool :as pool]
   [top.kzre.krro.plugin.painting.canvas.brush :as brush]
   [top.kzre.krro.plugin.painting.canvas.layer :as layer]
   [top.kzre.krro.plugin.painting.canvas.state :as state]
   [top.kzre.krro.plugin.painting.canvas.undo :as undo])
  (:import
    [javafx.animation AnimationTimer]
    (top.kzre.krro.canvas.core Arrays)))

(defn preview-stroke-events!
  "处理一帧的预览渲染：从事件队列中取出新事件，生成笔触片段，应用起笔锥化后
   直接绘制到当前图层像素，再将图层内容渲染到预览缓冲区并上传到 JavaFX Canvas。"
  [runtime upload-fn w h get-brush preview target-pixels]
  (let [new-evs (state/drain-new-events runtime)]
    (when (seq new-evs)
      (let [b      (get-brush)
            src    (target-pixels)
            stroke (brush-core/events->stroke b new-evs (:spacing b) (:radius b))
            global-end (state/get-stroke-length runtime)
            tapered (taper/taper-stroke-start stroke (:taper-start b)
                                              :fields [:radius :opacity]
                                              :end-dist global-end)]
        (brush-core/render-stroke! src w h tapered)    ; 直接在图层上绘制（预览专用）
        (layer/render-canvas! runtime preview)         ; 将图层合成结果拷入预览缓冲
        (upload-fn preview w h)))))



(defn commit-stroke!
  "结束当前笔画：借用临时缓冲区，渲染笔触，记录 undo，将结果拷贝到图层和预览缓冲区，归还临时缓冲。"
  [runtime upload-fn w h get-brush target-pixels]
  (let [all-evs (state/get-all-events runtime)
        b       (get-brush)
        dest    (target-pixels)
        layer-buf (state/layer-buffer runtime)
        preview (state/preview-buffer runtime)]
    (when (and (seq all-evs) dest)
      (let [buf-size (alength dest)
            temp (pool/borrow buf-size)]
        (try
          ;; 1. 拷贝原图到临时缓冲区
          (Arrays/copy layer-buf temp)
          ;; 2. 渲染最终笔触到临时缓冲区
          (let [stroke     (brush-core/events->stroke b all-evs (:spacing b) (:radius b))
                global-end (state/get-stroke-length runtime)
                tapered    (taper/taper-stroke stroke (:taper-start b) (:taper-end b)
                                               :fields [:radius :opacity]
                                               :end-dist global-end)
                dirties    (brush-core/render-stroke-dirties! layer-buf w h tapered)]
            ;; 3. 记录 undo 状态（此时 dest 还未修改）
            (undo/record-raster-state! runtime temp layer-buf dirties)
            ;; 4. 将结果一次性拷贝到 dest 和 preview
            (Arrays/copy layer-buf dest preview)
            (upload-fn preview w h))
          (finally
            (pool/return temp)))))))

(defn make-loop
  "创建渲染循环控制器。返回包含 :timer (AnimationTimer) 和 :commit (提交函数) 的 map。"
  [runtime upload-fn]
  (layer/auto-select-layer! runtime)
  (let [cd            (state/get-canvas-data runtime)
        w             (long (.width cd))
        h             (long (.height cd))
        get-brush     #(or @brush/global-brush brush/default-brush) ;; 使用全局笔刷，后备默认
        preview       (state/preview-buffer runtime)

        target-pixels #(when-let [l (layer/get-selected-layer runtime)]
                         (cp/data (:canvas l)))
        ;; AnimationTimer：每帧驱动预览
        timer (proxy [AnimationTimer] []
                (handle [_]
                  (try
                    (preview-stroke-events! runtime upload-fn w h get-brush preview target-pixels)
                    (catch Exception e
                      (log/error e "Render loop error")))))
        ;; 提交函数：笔画结束时调用
        commit-fn #(commit-stroke! runtime upload-fn w h get-brush target-pixels)]
    {:timer timer :commit commit-fn}))
(ns top.kzre.krro.plugin.painting.canvas.loop
  (:require
    [taoensso.timbre :as log]
    [top.kzre.krro.brush.core :as brush-core]
    [top.kzre.krro.brush.taper :as taper]
    [top.kzre.krro.canvas.core.canvas.protocol :as cp]
    [top.kzre.krro.canvas.core.floats-pool :as pool]
    [top.kzre.krro.core.message :as msg]
    [top.kzre.krro.plugin.painting.canvas.brush :as brush]
    [top.kzre.krro.plugin.painting.canvas.layer :as layer]
    [top.kzre.krro.plugin.painting.canvas.state :as state]
    [top.kzre.krro.plugin.painting.canvas.undo :as undo])
  (:import
    (javafx.animation AnimationTimer)
    (top.kzre.krro.canvas.core Arrays)))

(defn preview-stroke-events!
  "处理一帧的预览渲染."
  [runtime w h get-brush  get-target-pixels]
  (let [new-evs (state/drain-new-events runtime)]
    (when (seq new-evs)
      (if-let [src (get-target-pixels)]
        (let [b (get-brush)
              stroke (brush-core/events->stroke b new-evs (:spacing b) (:radius b))
              global-end (state/get-stroke-length runtime)
              tapered (taper/taper-stroke-start stroke (:taper-start b)
                                                :fields [:radius :opacity]
                                                :end-dist global-end)]
          (brush-core/render-stroke! src w h tapered))
        (msg/warn "No raster layer selected!")))))

(defn commit-stroke!
  "结束当前笔画。"
  [canvas-id runtime w h get-brush frame]  ;; 增加 frame 参数
  (if-let [selected-layer (layer/get-selected-layer frame canvas-id)]
    (let [all-evs (state/get-all-events runtime)
          b       (get-brush)
          layer-id (:id selected-layer)
          dest    (cp/data (:canvas selected-layer))
          layer-buf (state/layer-buffer runtime)]
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
              ;; 3. 记录 undo 状态（需要传入 frame）
              (undo/record-raster-stroke! canvas-id layer-id temp layer-buf dirties)
              ;; 4. 将结果拷贝到 dest 和 运行图层.
              (Arrays/copy layer-buf dest)
              (layer/refresh-canvas-frames! canvas-id))
            (finally
              (pool/return temp))))))
    (msg/warn "No layer selected!")))

(defn make-loop
  "创建渲染循环控制器。现在需要显式传入 frame。"
  [canvas-id runtime w h frame]   ;; 增加 frame 参数
  (let [get-brush     #(or @brush/global-brush brush/default-brush)
        ;; 使用 frame 获取当前选中图层的像素数据
        get-target-pixels #(when-let [l (layer/get-selected-layer frame canvas-id)]
                                (cp/data (:canvas l)))
        timer (proxy [AnimationTimer] []
                (handle [_]
                  (try
                    (preview-stroke-events! runtime w h get-brush  get-target-pixels)
                    (layer/refresh-canvas-frames! canvas-id)
                    (catch Exception e
                      (log/error e (str "Render loop error: "
                                        (.getMessage e)))))))
        ;; 提交函数现在捕获 frame
        commit-fn #(commit-stroke! canvas-id runtime w h get-brush frame)]
    {:timer timer :commit commit-fn}))
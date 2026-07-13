(ns top.kzre.krro.plugin.painting.stroke.raster-brush
  (:require
    [top.kzre.krro.brush.core :as brush-core]
    [top.kzre.krro.brush.taper :as taper]
    [top.kzre.krro.canvas.core.canvas.protocol :as cp]
    [top.kzre.krro.canvas.core.floats-pool :as pool]
    [top.kzre.krro.plugin.painting.canvas.brush :as brush]
    [top.kzre.krro.plugin.painting.canvas.state :as state]
    [top.kzre.krro.plugin.painting.canvas.undo :as undo]
    [top.kzre.krro.plugin.painting.project.canvas :as pc])
  (:import
    [top.kzre.krro.canvas.core Arrays]))

(defn- get-brush []
  (or @brush/global-brush brush/default-brush))

(defn preview!
  "光栅图层的笔画预览：直接在图层像素数据上绘制当前帧事件。"
  [canvas-id]
  (when-let [layer (state/selected-layer! canvas-id)]
    (let [src     (cp/data (:canvas layer))
          new-evs (state/drain-new-events! canvas-id)]
      (when (and src (seq new-evs))
        (let [b          (get-brush)
              [w h]      (pc/canvas-size canvas-id)
              stroke     (brush-core/events->stroke b new-evs (:spacing b) (:radius b))
              global-end (state/get-stroke-length-by-id canvas-id)
              tapered    (taper/taper-stroke-start stroke (:taper-start b)
                                                   :fields [:radius :opacity]
                                                   :end-dist global-end)]
          (brush-core/render-stroke! src w h tapered))))))

(defn commit!
  "提交光栅笔画。"
  [canvas-id]
  (let [layer    (state/selected-layer! canvas-id)
        layer-id (:id layer)
        dest     (cp/data (:canvas layer))
        layer-buf (state/layer-buffer-by-id canvas-id)
        all-evs  (state/get-all-events-by-id canvas-id)]
    (when (and layer (seq all-evs) dest)
      (let [b        (get-brush)
            [w h]    (pc/canvas-size canvas-id)
            buf-size (alength dest)
            temp     (pool/borrow buf-size)]
        (try
          (Arrays/copy layer-buf temp)
          (let [stroke     (brush-core/events->stroke b all-evs (:spacing b) (:radius b))
                global-end (state/get-stroke-length-by-id canvas-id)
                tapered    (taper/taper-stroke stroke (:taper-start b) (:taper-end b)
                                               :fields [:radius :opacity]
                                               :end-dist global-end)
                dirties    (brush-core/render-stroke-dirties! layer-buf w h tapered)]
            (undo/record-raster-stroke! canvas-id layer-id temp layer-buf dirties)
            (Arrays/copy layer-buf dest))
          (finally
            (pool/return temp)))))))
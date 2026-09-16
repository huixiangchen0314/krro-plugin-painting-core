(ns top.kzre.krro.plugin.painting.core.schedule.util
  (:require
   [top.kzre.krro.canvas.core.layer.transform :as trans]
   [top.kzre.krro.plugin.painting.core.schedule.protocol :as proto])
  (:import
   [java.util Set]
   (top.kzre.krro.canvas.core.layer LayerUtils)
   (top.kzre.krro.plugin.painting.core.schedule.protocol ILayer)
   (top.kzre.krro.util.math KMath)
   [top.kzre.krro.util.tile CanvasUtils]))

(defn dirty-region
  [region transform viewport-w viewport-h tile-size]
  (cond
    (nil? region) (set (LayerUtils/canvasTiles tile-size viewport-w viewport-h))
    (not (seq region)) #{}
    (or (set? region) (instance? Set region))
    (if transform
      (let [screen-tiles (LayerUtils/transformTiles region tile-size transform)]
        (if (and viewport-w viewport-h)
          (set (CanvasUtils/clipTiles screen-tiles tile-size viewport-w viewport-h))
          screen-tiles))
      (if (and viewport-w viewport-h)
        (set (CanvasUtils/clipTiles region tile-size viewport-w viewport-h))
        region))
    (map? region)
    (let [{:keys [min-x min-y max-x max-y]} region
          corners        [[min-x min-y] [max-x min-y]
                          [max-x max-y] [min-x max-y]]
          screen-corners (if transform
                           (map (fn [[x y]]
                                  (KMath/mat2dTransformPoint transform (float x) (float y)))
                                corners)
                           (map (fn [[x y]] [(double x) (double y)]) corners))
          xs             (map first screen-corners)
          ys             (map second screen-corners)
          screen-min-x   (apply min xs)
          screen-max-x   (apply max xs)
          screen-min-y   (apply min ys)
          screen-max-y   (apply max ys)
          clipped-min-x  (max screen-min-x 0.0)
          clipped-max-x  (min screen-max-x (double viewport-w))
          clipped-min-y  (max screen-min-y 0.0)
          clipped-max-y  (min screen-max-y (double viewport-h))]
      (if (and (< clipped-min-x clipped-max-x)
               (< clipped-min-y clipped-max-y))
        (set (LayerUtils/aabbTiles tile-size
                                   clipped-min-x clipped-min-y
                                   clipped-max-x clipped-max-y))
        #{}))
    :else
    (throw (IllegalArgumentException.
             (str "region must be Set or Map, got " (type region))))))


(defn ->raster-layer
  "把标准图层协议转换为光栅图层"
  [^ILayer layer
   & {:keys [backend]
      :or {backend :default}}]
  {:id         (proto/layer-id layer)
   :type       :raster
   :opacity    (proto/opacity layer)
   :blend-mode (proto/blend-mode layer)
   :visible   (proto/visible? layer)
   :canvas    (proto/canvas layer)
   :transform (proto/transform layer)
   :backend    backend})


(defn compose-transform
  "合成图层变换"
  [layers & [view-matrix]]
  (mapv #(trans/compose-transforms % :viewport view-matrix) layers))
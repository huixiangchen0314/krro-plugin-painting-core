(ns top.kzre.krro.plugin.painting.core.schedule.raster-layer
  (:require
    [top.kzre.krro.canvas.core.layer.util :as util]
    [top.kzre.krro.plugin.painting.core.schedule.protocol :as proto]
    [top.kzre.krro.core.util.promise :as promise]))

;; 将光栅图层适配为原始输入节点
(defrecord RasterLayer [layer]
  proto/ILayer
  (layer-id [_] (:id layer))
  (opacity [_] (:opacity layer))
  (canvas [_] (:canvas layer))
  (transform [_] (:transform layer))
  (blend-mode [_] (util/blend-mode-str (:blend-mode layer) :normal)))

;; 将光栅图层适配为渲染节点
(defrecord RasterLayerNode [^RasterLayer layer]
  proto/IRenderNode
  (node-key [_] (proto/layer-id layer))
  (inputs [_] [] [])
  (set-caching! [_ _] nil)
  (caching? [_] false)
  (cached? [_] false)
  (invalidate-cache! [_] nil)
  (request! [_ _ctx]
    (promise/resolved layer)))

(defn make-raster-layer-node [raster-layer]
  (->RasterLayerNode (->RasterLayer raster-layer)))
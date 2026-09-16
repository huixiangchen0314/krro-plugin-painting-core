(ns top.kzre.krro.plugin.painting.core.schedule.raster-layer
  (:require
    [top.kzre.krro.canvas.core.layer.util :as util]
    [top.kzre.krro.core.util.computing-graph :as cg]
    [top.kzre.krro.plugin.painting.core.schedule.protocol :as proto]
    [top.kzre.krro.core.util.promise :as promise]))

;; 将光栅图层适配为原始输入节点
(defrecord RasterLayer [layer]
  proto/ILayer
  (opacity [_] (:opacity layer))
  (canvas [_] (:canvas layer))
  (visible? [_] (:visible layer))
  (transform [_] (:transform layer))
  (blend-mode [_] (util/blend-mode-str (:blend-mode layer) :normal)))

;; 将光栅图层适配为渲染节点
(defrecord RasterLayerNode [^RasterLayer layer]
  ;; 计算节点协议
  cg/INode
  (node-id [_] (:id (:layer layer)))
  (dependencies [_] #{})
  (compute [_ _]
    (promise/resolved layer)))

(defn make-raster-layer-node [raster-layer]
  (->RasterLayerNode (->RasterLayer raster-layer)))
(ns top.kzre.krro.plugin.painting.core.project.vector-layer
  "矢量图层数据持久化与激活。
   矢量数据为纯 EDN 描述，无需侧表。激活/钝化直接返回图层。"
  (:require
    [top.kzre.krro.plugin.painting.core.project.canvas :as canvas]))

(defmethod canvas/persistable-layer :vector [layer]
  layer)

(defmethod canvas/persistable-layer! :vector [layer _canvas-id]
  layer)

(defmethod canvas/active-layer! :vector [layer _canvas-id]
  layer)
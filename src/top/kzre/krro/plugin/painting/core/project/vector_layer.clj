(ns top.kzre.krro.plugin.painting.core.project.vector-layer
  "矢量图层数据持久化与激活。
   矢量数据为纯 EDN 描述，无需侧表。激活/钝化直接返回图层。"
  (:require
    [top.kzre.krro.plugin.painting.core.project.canvas :as canvas]))

(defn paths
  "返回图层的路径映射 {path-id path-data}。"
  [layer]
  (:paths-map layer))

(defn assoc-paths [layer paths]
  (assoc layer :paths-map paths))

(defn update-paths [layer f & args]
  (apply update layer :paths-map f args))

(defn path-order
  "返回图层的路径顺序（向量）。"
  [layer]
  (:path-order layer []))

(defn path-count
  "返回图层中的路径数量。"
  [layer]
  (count (paths layer)))

(defn path-exists?
  "检查指定路径 ID 是否存在于图层中。"
  [layer path-id]
  (contains? (paths layer) path-id))


(defmethod canvas/persistable-layer :vector [layer]
  layer)

(defmethod canvas/persistable-layer! :vector [layer _canvas-id]
  layer)

(defmethod canvas/active-layer! :vector [layer _canvas-id]
  layer)
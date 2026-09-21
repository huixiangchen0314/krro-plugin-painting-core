(ns top.kzre.krro.plugin.painting.core.project.vector-layer
  "矢量图层数据持久化与激活。
   矢量数据为纯 EDN 描述，无需侧表。激活/钝化直接返回图层。"
  (:require
    [top.kzre.krro.canvas.vector.core :as canvas.vector]
    [top.kzre.krro.plugin.painting.core.project.canvas :as pc])
  (:import (top.kzre.krro.canvas.vector.anchor Anchor)))

(defn ^:deprecated paths
  "返回图层的路径映射 {path-id path-data}。"
  [layer]
  (:paths layer {}))

(defn path-tiles
  "返回路径在全局瓦片大小下覆盖的瓦片"
  [path]
  (canvas.vector/path-tiles path pc/global-tile-size))

(defn seg-tiles [path idx]
  (canvas.vector/seg-tiles path idx pc/global-tile-size))

;; project.vector-layer
(defn anchor-tiles
  "锚点两侧段的瓦片集合。
   与 canvas.vector/anchor-tiles 同签名，注入 global-tile-size。"
  [paths ^Anchor anchor]
  (canvas.vector/anchor-tiles paths anchor pc/global-tile-size))

(defmethod pc/persistable-layer :vector [layer]
  (-> layer
    (update :paths
            (fn [paths]
              (into {} (map (fn [[path-id path]]
                              [path-id (dissoc path :arc-params)])
                            paths))))))

(defmethod pc/persistable-layer! :vector [layer _canvas-id]
  (pc/persistable-layer layer))

(defmethod pc/active-layer! :vector [layer _canvas-id]
  layer)



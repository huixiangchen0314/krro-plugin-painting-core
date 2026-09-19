(ns top.kzre.krro.plugin.painting.core.project.vector-layer
  "矢量图层数据持久化与激活。
   矢量数据为纯 EDN 描述，无需侧表。激活/钝化直接返回图层。"
  (:require
    [top.kzre.krro.canvas.vector.core :as vector-core]
    [top.kzre.krro.plugin.painting.core.project.canvas :as pc]))

(defn paths
  "返回图层的路径映射 {path-id path-data}。"
  [layer]
  (:paths layer))

(defn assoc-paths [layer paths]
  (assoc layer :paths paths))

(defn path-tiles
  "返回路径在全局瓦片大小下覆盖的瓦片"
  [path]
  (vector-core/path-tiles path pc/global-tile-size))

(defn path-order
  "返回图层的路径顺序（向量）。"
  [layer]
  (:path-order layer []))


(defn fresh-path-id []
  (keyword (str "path-" (gensym))))

(defn add-path
  ([layer path]
   (add-path layer (fresh-path-id) path))
  ([layer path-id path]
   {:pre [(some? path-id)]}
   (-> layer
       (update :paths assoc path-id path)
       (update :path-order conj path-id))))


(defn remove-path
  [layer path-id]
  (-> layer
      (update :paths dissoc path-id)
      (update :path-order (fn [order] (vec (remove #{path-id} order))))))

(defn save-path
  [layer path-id path]
  (let [ps (paths layer)]
    (if (get ps path-id)
      (update layer :paths assoc path-id path)
      (add-path layer path-id path))))

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
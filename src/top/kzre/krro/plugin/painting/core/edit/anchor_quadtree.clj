(ns top.kzre.krro.plugin.painting.core.edit.anchor-quadtree
  (:require [top.kzre.krro.canvas.core.layer.util :as util]
            [top.kzre.krro.plugin.painting.core.algo.anchor-quadtree :as anchor-quadtree])
  (:import (top.kzre.krro.canvas.core QuadTree)))

;; 锚点四叉树加速结构
(defonce anchor-quadtrees (atom {}))


(defn anchor-quadtree-interceptor
  "更新前确保 anchor-quadtree存在"
  []
  {:before
   (fn [context]
     ;; 确保派生数据
     (when-let [canvas-id (get-in context [:coeffects :record-id ])]
       (when-not (get @anchor-quadtrees canvas-id)
         (let [canvas-data (get-in context [:coeffects :record :canvas-data])]
           (when-let [current-layer-id (:current-layer-id canvas-data)]
             (let [layers (:layers canvas-data)]
               (when-let [layer (util/find-layer current-layer-id layers)]
                 (when (= :vector (:type layer))
                   (let [paths (:paths-map layer)
                         tree (anchor-quadtree/build-anchor-quadtree paths)]
                     (swap! anchor-quadtrees assoc canvas-id tree)))))))))
     context)})



(defn update-anchor-quadtree!
  "增量更新画布的锚点四叉树：删除旧点，插入新点。
   old-paths 和 new-paths 是路径映射，anchors 是修改的锚点集合。"
  [canvas-id old-paths new-paths anchors]
  (let [^QuadTree tree (get @anchor-quadtrees canvas-id)]
    (if tree
      (anchor-quadtree/update-anchor-quadtree! tree old-paths new-paths anchors)
      ;; 树不存在，完全重建
      (let [new-tree (anchor-quadtree/build-anchor-quadtree new-paths)]
        (swap! anchor-quadtrees assoc canvas-id new-tree)))))
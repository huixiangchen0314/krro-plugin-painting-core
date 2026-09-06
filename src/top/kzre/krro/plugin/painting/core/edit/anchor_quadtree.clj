(ns top.kzre.krro.plugin.painting.core.edit.anchor-quadtree
  (:require [top.kzre.krro.canvas.core.layer.util :as util]
            [top.kzre.krro.plugin.painting.core.algo.anchor-quadtree :as anchor-quadtree])
  (:import (top.kzre.krro.canvas.core QuadTree)))

;; 锚点四叉树加速结构
(defonce anchor-quadtrees (atom {}))

(defmacro quadtree-fn
  "获取 canvas-id 对应的四叉树，若存在则调用 fn-sym，并将 tree 作为第一个参数插入。
   (quadtree-fn canvas-id anchor-quadtree/delete-anchors! paths anchors)
   展开为：
   (when-let [tree (get @anchor-quadtrees canvas-id)]
     (anchor-quadtree/delete-anchors! tree paths anchors))"
  [canvas-id fn-sym & args]
  `(when-let [~'tree (get @anchor-quadtrees ~canvas-id)]
     (~fn-sym ~'tree ~@args)))

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

(defn update-anchors!
  "增量更新画布的锚点四叉树：删除旧点，插入新点。
   old-paths 和 new-paths 是路径映射，anchors 是修改的锚点集合。
   注意：调用此函数前应确保四叉树已存在（由 interceptor 或初始构建保证）。"
  [canvas-id old-paths new-paths anchors]
  (quadtree-fn canvas-id anchor-quadtree/update-anchors! old-paths new-paths anchors))

(defn delete-anchors!
  [canvas-id paths anchors]
  (quadtree-fn canvas-id anchor-quadtree/delete-anchors! paths anchors))

(defn insert-anchors!
  [canvas-id paths anchors]
  (quadtree-fn canvas-id anchor-quadtree/insert-anchors! paths anchors))

(defn delete-path!
  [canvas-id paths path-id]
  (quadtree-fn canvas-id anchor-quadtree/delete-path! paths path-id))

(defn insert-path!
  [canvas-id paths path-id]
  (quadtree-fn canvas-id anchor-quadtree/insert-path! paths path-id))
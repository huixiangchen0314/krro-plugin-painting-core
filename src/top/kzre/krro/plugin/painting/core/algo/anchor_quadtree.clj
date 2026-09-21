(ns top.kzre.krro.plugin.painting.core.algo.anchor-quadtree
  (:require
    [top.kzre.krro.canvas.vector.core :as cv])
  (:import (top.kzre.krro.canvas.core QuadTree)))

(defn build-anchor-quadtree
  "从矢量图层构建锚点四叉树。"
  [paths]
  (let [tree (QuadTree.)]
    (doseq [[path-id path] paths
            :let [curve (:curve path)
                  points (:points curve)]]
      (doseq [idx (range (count points))
              :let [point (nth points idx)]]
        (.insert tree (:x point) (:y point)
                 (cv/->Anchor path-id idx))))
    tree))

(defn update-anchors! [^QuadTree tree old-paths new-paths anchors]
  (doseq [anchor anchors]
    ;; 删除旧点
    (let [pts (get-in old-paths [(:path-id anchor) :curve :points])
          pt  (nth pts (:point-idx anchor))]
      (.delete tree (:x pt) (:y pt) anchor))
    ;; 插入新点
    (let [pts (get-in new-paths [(:path-id anchor) :curve :points])
          pt  (nth pts (:point-idx anchor))]
      (.insert tree (:x pt) (:y pt) anchor)))
  tree)

(defn delete-anchors!
  "从四叉树中删除指定的锚点。"
  [^QuadTree tree paths anchors]
  (doseq [anchor anchors]
    (let [pts (get-in paths [(:path-id anchor) :curve :points])
          pt (nth pts (:point-idx anchor))]
      (.delete tree (:x pt) (:y pt) anchor))))

(defn delete-path! [^QuadTree tree paths path-id]
  (when-let [path (get paths path-id)]
    (let [anchors (cv/all-anchors paths path-id)]
      (doseq [anchor anchors]
        (let [pts (get-in path [:curve :points])
              pt (nth pts (:point-idx anchor))]
          (.delete tree (:x pt) (:y pt) anchor))))))

(defn insert-anchors!
  "向四叉树中插入指定的锚点。"
  [^QuadTree tree paths anchors]
  (doseq [anchor anchors]
    (let [pts (get-in paths [(:path-id anchor) :curve :points])
          pt (nth pts (:point-idx anchor))]
      (.insert tree (:x pt) (:y pt) anchor))))

(defn insert-path! [^QuadTree tree paths path-id]
  (when-let [path (get paths path-id)]
    (let [anchors (cv/all-anchors paths path-id)]
      (doseq [anchor anchors]
        (let [pts (get-in path [:curve :points])
              pt (nth pts (:point-idx anchor))]
          (.insert tree (:x pt) (:y pt) anchor))))))
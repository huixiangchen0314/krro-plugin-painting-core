(ns top.kzre.krro.plugin.painting.core.algo.anchor-quadtree
  (:import (top.kzre.krro.canvas.core QuadTree))
  (:require
    [top.kzre.krro.plugin.painting.core.algo.anchor :as anchor]))

(defn build-anchor-quadtree
  "从矢量图层构建锚点四叉树。"
  [paths]
  (let [tree (QuadTree.)]
    (doseq [[path-id path] paths
            :let [curve (:bezier-curve path)
                  points (:points curve)]]
      (doseq [idx (range (count points))
              :let [point (nth points idx)]]
        (.insert tree (:x point) (:y point)
                 (anchor/->Anchor path-id idx))))
    tree))

(defn update-anchor-quadtree! [^QuadTree tree old-paths new-paths anchors]
  (doseq [anchor anchors]
    ;; 删除旧点
    (let [pts (get-in old-paths [(:path-id anchor) :bezier-curve :points])
          pt  (nth pts (:point-idx anchor))]
      (.delete tree (:x pt) (:y pt) anchor))
    ;; 插入新点
    (let [pts (get-in new-paths [(:path-id anchor) :bezier-curve :points])
          pt  (nth pts (:point-idx anchor))]
      (.insert tree (:x pt) (:y pt) anchor)))
  tree)
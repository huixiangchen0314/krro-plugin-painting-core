(ns top.kzre.krro.plugin.painting.core.algo.segment-quadtree
  (:require
   [top.kzre.krro.curve.bezier2d.core :as bezier]
   [top.kzre.krro.plugin.painting.core.algo.segment :as segment])
  (:import
    (top.kzre.krro.canvas.core Rect RectQuadTree)))

(defn build-segment-quadtree
  "从矢量图层构建曲线分段四叉树。"
  [paths]
  (let [tree (RectQuadTree.)]
    (doseq [[path-id path] paths
            :let [curve (:bezier-curve path)
                  points (:points curve)]]
      (doseq [idx (range (- (count points) 1))
              :let [seg (segment/->Segment path-id idx)
                    aabb (bezier/aabb curve idx)
                    {:keys [min-x min-y max-x max-y]} aabb
                    rect (Rect. min-x min-y max-x max-y)]]
        (.insert tree rect seg)))
    tree))
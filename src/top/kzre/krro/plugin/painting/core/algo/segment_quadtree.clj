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

(defn update-segment-quadtree
  "增量更新段四叉树：删除旧段，插入新段。"
  [^RectQuadTree tree old-paths new-paths segments]
  ;; 删除旧段
  (doseq [seg segments]
    (let [path-id (:path-id seg)
          seg-idx (:segment-idx seg)
          old-curve (get-in old-paths [path-id :bezier-curve])
          old-aabb (when old-curve (bezier/seg-aabb old-curve seg-idx))]
      (when old-aabb
        (let [rect (Rect. (:min-x old-aabb) (:min-y old-aabb)
                          (:max-x old-aabb) (:max-y old-aabb))]
          (.delete tree rect seg)))))
  ;; 插入新段
  (doseq [seg segments]
    (let [path-id (:path-id seg)
          seg-idx (:segment-idx seg)
          new-curve (get-in new-paths [path-id :bezier-curve])
          new-aabb (when new-curve (bezier/seg-aabb new-curve seg-idx))]
      (when new-aabb
        (let [rect (Rect. (:min-x new-aabb) (:min-y new-aabb)
                          (:max-x new-aabb) (:max-y new-aabb))]
          (.insert tree rect seg)))))
  tree)
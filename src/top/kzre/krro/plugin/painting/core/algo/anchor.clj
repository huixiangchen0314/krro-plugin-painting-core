(ns top.kzre.krro.plugin.painting.core.algo.anchor
  (:require [top.kzre.krro.curve.bezier2d.core :as bezier]))

(defrecord Anchor [path-id point-idx])

(defn translate-anchors
  "移动锚点，返回新路径和更新的 aabb"
  [paths anchors dx dy]
  (let [anchor-groups (group-by :path-id anchors)
        [new-paths aabb]
        (reduce
          (fn [[paths aabb] [path-id anchors]]
            (if-let [path (get paths path-id)]
              (let [idxs (mapv :point-idx anchors)
                    old-curve (:bezier-curve path)
                    new-curve (apply bezier/translate old-curve dx dy idxs)
                    old-aabb (apply bezier/aabb old-curve idxs)
                    new-aabb (apply bezier/aabb new-curve idxs)
                    merged (bezier/merge-aabb old-aabb new-aabb aabb)]
                [(assoc paths path-id (assoc path :bezier-curve new-curve))
                 merged])
              [paths aabb]))
          [paths nil]
          anchor-groups)]
    {:paths new-paths
     :aabb aabb}))
(ns top.kzre.krro.plugin.painting.core.algo.anchor
  (:require [top.kzre.krro.curve.bezier2d.core :as bezier]))

(defrecord Anchor [path-id point-idx])

;; TODO aabb 考虑 曲线线宽
(defn aabb
  "计算锚点集合的包围盒（世界坐标），返回 {:min-x, :min-y, :max-x, :max-y}。"
  [paths anchors]
  (when (seq anchors)
    (reduce (fn [acc {:keys [path-id point-idx]}]
              (let [curve (get-in paths [path-id :bezier-curve])
                    aabb (bezier/aabb curve point-idx)]
                (bezier/merge-aabb aabb acc)))
            nil
            anchors)))


(defn translate-anchors
  "移动锚点，返回新路径和更新的 aabb"
  [paths anchors dx dy]
  (let [anchor-groups (group-by :path-id anchors)
        new-paths
        (reduce
          (fn [acc [path-id anchors]]
            (if-let [path (get acc path-id)]
              (let [idxs (mapv :point-idx anchors)
                    ;; TODO catmull-rom 分支
                    old-curve (:bezier-curve path)
                    new-curve (apply bezier/translate old-curve dx dy idxs)]
                (assoc acc path-id (assoc path :bezier-curve new-curve)))
              acc))
          paths
          anchor-groups)
        old-aabb (aabb paths anchors)
        new-aabb (aabb new-paths anchors)
        all-aabb (bezier/merge-aabb old-aabb new-aabb)]
    {:paths new-paths
     :aabb all-aabb}))
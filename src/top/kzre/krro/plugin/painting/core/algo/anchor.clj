(ns top.kzre.krro.plugin.painting.core.algo.anchor
  (:require [top.kzre.krro.curve.bezier2d.core :as bezier])
  (:import (top.kzre.curve.bezier2d ChordLengthTable)))

(defrecord Anchor [path-id point-idx])

(defn path-containing-anchor
  "从路径映射 paths 中查找包含 anchor 的路径数据。
   若路径存在则返回路径 map，否则返回 nil。"
  [paths ^Anchor anchor]
  (get paths (:path-id anchor)))

(defn anchor-point
  "返回锚点所在的控制点坐标（作为 map {:x :y}）。"
  [paths ^Anchor anchor]
  (when-let [path (path-containing-anchor paths anchor)]
    (let [curve (case (:path-type path)
                  :bezier (:bezier-curve path)
                  :catmull-rom (:cr-curve path)
                  nil)]
      (when curve
        (let [points (:points curve)
              idx (:point-idx anchor)]
          (when (and points (< idx (count points)))
            (nth points idx)))))))

(defn max-path-width
  [path]
  (let [stroke (get-in path [:style :stroke])]
     (cond
       (and stroke (:width-samples stroke))
       (apply max (:width-samples stroke))
       (and stroke (:width stroke))
       (:width stroke)
       :else 0)))

(defn aabb
  "计算锚点集合的包围盒（世界坐标），考虑描边宽度，返回 {:min-x, :min-y, :max-x, :max-y}。
   - 若路径有 stroke 且包含 :width-samples，则取最大值作为线宽。
   - 若无 :width-samples 但有 :width，则使用该值。
   - 若无 stroke，线宽为 0。
   包围盒向外扩展线宽的一半（因为描边从中心线向两侧延伸）。"
  ([paths anchors]
   (let [anchor-groups (group-by :path-id anchors)]
     (when (seq anchors)
       (reduce
         (fn [acc [path-id anchors]]
           (let [path (get paths path-id)
                 stroke-width (max-path-width path)
                 half-width (/ stroke-width 2)
                 ;; 计算该路径所有锚点的 AABB，基于每个锚点的 point-idx
                 ;; 对每个锚点单独计算 AABB 并合并
                 path-aabb
                 (when-let [curve (:bezier-curve path)]
                   (if (> half-width 0)
                     (reduce (fn [acc2 {:keys [point-idx]}]
                               (let [aabb (bezier/aabb curve point-idx)
                                     ;; 扩展线宽
                                     expanded (-> aabb
                                                  (update :min-x - half-width)
                                                  (update :min-y - half-width)
                                                  (update :max-x + half-width)
                                                  (update :max-y + half-width))]
                                 (bezier/merge-aabb expanded acc2)))
                             nil
                             anchors)
                     (bezier/aabb curve)))
                 ]
             (bezier/merge-aabb path-aabb acc)))
         nil
         anchor-groups))))
  ([paths1 path2 anchors]
   (let [old-aabb (aabb paths1 anchors)
         new-aabb (aabb path2 anchors)]
     (bezier/merge-aabb old-aabb new-aabb))))



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
          anchor-groups)]
    {:paths new-paths
     :aabb (aabb paths new-paths anchors)}))

(defn ensure-width-samples
  "确保路径的 stroke 包含宽度采样，若不存在则用当前 :stroke :width 初始化。
   使用 ChordLengthTable 计算真实的弦长参数。"
  ([path] (ensure-width-samples path nil))
  ([path brush]
   (let [style (:style path)
         stroke (:stroke style)
         width (or (:width stroke) (:radius brush) 1.0)
         samples (:width-samples stroke)
         arc-params (:arc-params stroke)]
     (if (and samples arc-params (seq samples))
       path
       (let [curve (:bezier-curve path)
             points (:points curve)
             num-points (count points)]
         (if (> num-points 1)
           (let [xs (mapv :x points)
                 ys (mapv :y points)
                 chord-table (ChordLengthTable. (double-array xs) (double-array ys))
                 t-params (.getParameters chord-table)  ; 真实的弦长参数
                 new-samples (vec (repeat num-points width))
                 new-arc-params (vec t-params)]
             (-> path
                 (assoc-in [:style :stroke :width-samples] new-samples)
                 (assoc-in [:style :stroke :arc-params] new-arc-params)))
           path))))))


;; TODO 宽度采样，控制点数量独立
(defn adjust-widths
  "对选中的锚点应用宽度增量。返回更新后的 paths map。
   支持 min-width 和 max-width 钳制，默认 min-width 0.1，max-width 无限。"
  [paths anchors delta
   & {:keys [min-width max-width]
      :or {min-width 0.1
           max-width Double/POSITIVE_INFINITY}}]
  (let [groups (group-by :path-id anchors)
        new-paths
        (reduce
          (fn [acc [path-id anchors]]
            (let [path (get acc path-id)
                  path (ensure-width-samples path)
                  stroke (get-in path [:style :stroke])
                  samples (:width-samples stroke)
                  idxs (mapv :point-idx anchors)
                  new-samples (map-indexed
                                (fn [idx width]
                                  (if (contains? idxs idx)
                                    (-> (+ width delta)
                                        (max min-width)
                                        (min max-width))
                                    width)) samples)
                  new-stroke (assoc stroke :width-samples new-samples)]
              (assoc-in acc [path-id :style :stroke] new-stroke)))
          paths
          groups)]
    {:paths new-paths
     :aabb (aabb paths new-paths anchors)}))

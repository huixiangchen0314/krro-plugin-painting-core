(ns top.kzre.krro.plugin.painting.core.algo.anchor
  (:require [top.kzre.krro.curve.bezier2d.core :as bezier])
  (:import (top.kzre.curve.bezier2d ChordLengthTable)))

(defrecord Anchor [path-id point-idx])

(defn anchors
  "返回路径中所有锚点的列表（Anchor 记录）。"
  [paths path-id]
  (let [points (get-in paths [path-id :bezier-curve :points])]
    (mapv (fn [idx] (->Anchor path-id idx)) (range (count points)))))

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
  (let [stroke-width (get-in path [:style :stroke :width] 0)
        width-samples (:width-samples path)]
     (cond
       (seq width-samples)
       (apply max width-samples)
       :else stroke-width)))

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
   (let [width (or (get-in path [:style :stroke :width])
                   (:radius brush)
                   1.0)
         samples (:width-samples path)
         arc-params (:arc-params path)]
     (if (and samples (seq arc-params) (seq samples))
       path
       (let [curve (:bezier-curve path)
             points (:points curve)
             num-points (count points)]
         (if (> num-points 1)
           (let [xs (mapv :x points)
                 ys (mapv :y points)
                 chord-table (ChordLengthTable. (double-array xs) (double-array ys))
                 t-params (.getParameters chord-table)
                 new-samples (vec (repeat num-points width))
                 new-arc-params (vec t-params)]
             (-> path
                 (assoc :width-samples new-samples)
                 (assoc :arc-params new-arc-params)))
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
                  samples (:width-samples path)
                  new-samples
                  (map-indexed
                    (fn [idx width]
                      (if (some #(= idx (:point-idx %)) anchors)
                        (-> (+ width delta)
                            (max min-width)
                            (min max-width))
                        width)) samples)]
              (assoc acc path-id (assoc path :width-samples new-samples))))
          paths
          groups)]
    {:paths new-paths
     :aabb (aabb paths new-paths anchors)}))

(defn- extrude-bezier-start
  [path point]
  (let [curve (:bezier-curve path)
        points (:points curve)
        p0 (first points)
        new-point (-> (select-keys point [:x :y])
                      (assoc :dx1 0 :dy1 0
                             :dx2 0 :dy2  0))
        updated-p0 (-> p0
                       (assoc :dx1 0 :dy1 0))
        new-points (vec (concat [new-point] (cons updated-p0 (rest points))))]
    (assoc-in path [:bezier-curve :points] new-points)))

(defn- extrude-bezier-end
  [path point]
  (let [curve (:bezier-curve path)
        points (:points curve)
        p-last (last points)
        dx (- (:x point) (:x p-last))
        dy (- (:y point) (:y p-last))
        new-point (-> (select-keys point [:x :y])
                      (assoc :dx1 0 :dy1 0
                             :dx2 0 :dy2 0))
        updated-p-last (-> p-last
                           (assoc :dx2 0 :dy2 0))
        new-points (vec (concat (butlast points) [updated-p-last new-point]))]
    (assoc-in path [:bezier-curve :points] new-points)))

(defn end-anchor?
  "判断锚点是否为路径的端点（首点或尾点）"
  [paths ^Anchor anchor]
  (when-let [path (get paths (:path-id anchor))]
    (let [curve (:bezier-curve path)
          points (:points curve)
          idx (:point-idx anchor)]
      (and (not (:closed curve))
           (or (zero? idx)
               (= idx (dec (count points))))))))

(defn extrude-anchor
  "根据锚点挤出路径。如果锚点是首尾点，则在对应端挤出；否则返回 nil。
   返回 {:paths new-paths :aabb aabb :new-anchor Anchor}，或 nil。"
  [paths ^Anchor anchor point]
  (when (end-anchor? paths anchor)
    (let [path-id (:path-id anchor)
          path (get paths path-id)
          curve (:bezier-curve path)
          idx (:point-idx anchor)
          num-points (count (:points curve))]
      (when-let [new-path
                 (cond
                   (zero? idx) (extrude-bezier-start path point)
                   (= idx (dec num-points)) (extrude-bezier-end path point)
                   :else nil)]
        (let [new-paths (assoc paths path-id new-path)
              new-points (get-in new-path [:bezier-curve :points])
              new-anchor (if (zero? idx)
                           (->Anchor path-id 0)  ; 起点挤出，新点索引 0
                           (->Anchor path-id (dec (count new-points))))  ; 终点挤出，新点索引最后
              updated-anchor (if (zero? idx)
                               (->Anchor path-id 1)
                               anchor)]
          {:paths new-paths
           :aabb (aabb new-paths [new-anchor])
           :anchor updated-anchor
           :new-anchor new-anchor})))))
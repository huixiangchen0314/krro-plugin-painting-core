(ns top.kzre.krro.plugin.painting.core.algo.anchor
  (:require
   [top.kzre.krro.canvas.vector.core :as vector-core]
   [top.kzre.krro.plugin.painting.core.algo.path :as path]
   [top.kzre.krro.curve.bezier2d.core :as bezier])
  (:import
   (top.kzre.curve.bezier2d
    ArcLengthUtils
    Curve
    CurveExtrusionUtils
    Pair
    TableMapping)))

(defrecord Anchor [path-id point-idx])

(defn all-anchors
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

(defn aabb
  "计算锚点集合的包围盒（世界坐标），考虑描边宽度，返回 {:min-x, :min-y, :max-x, :max-y}。"
  ([paths anchors]
   (let [anchor-groups (group-by :path-id anchors)]
     (when (seq anchors)
       (reduce
         (fn [acc [path-id anchors]]
           (let [path (get paths path-id)
                 stroke-width (vector-core/max-path-width path)
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


(defn median-point
  "返回所选择锚点的质心点（所有锚点坐标的平均值）。
   若 anchors 为空或所有点都无效，返回 nil。"
  [paths anchors]
  (when (seq anchors)
    (let [points (keep #(anchor-point paths %) anchors)]
      (when (seq points)
        (let [xs (map :x points)
              ys (map :y points)
              n (count points)]
          {:x (/ (reduce + xs) n)
           :y (/ (reduce + ys) n)})))))

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


;; TODO 宽度采样，控制点数量独立
(defn adjust-widths
  [paths anchors delta
   & {:keys [min-width max-width]
      :or {min-width 0.1
           max-width Double/POSITIVE_INFINITY}}]
  (let [[new-paths aabb-anchors]
        (reduce
          (fn [[paths-acc aabb-anchors-acc] [path-id anchor-group]]
            (let [path (get paths-acc path-id)
                  width-type (path/path-width-type path)]
              (case width-type
                :fixed
                (let [old-width (get-in path [:style :stroke :width] 1.0)
                      new-width (-> (+ old-width delta)
                                    (max min-width)
                                    (min max-width))
                      new-path (-> path
                                   (assoc-in [:style :stroke :width] new-width)
                                   (dissoc :width-samples :arc-params :t-params)
                                   (assoc :width-type :fixed))]
                  [(assoc paths-acc path-id new-path)
                   (into aabb-anchors-acc (all-anchors paths path-id))])

                (:point-width :t-width)
                (let [path (path/ensure-width-type path width-type)
                      samples (:width-samples path)
                      idx-set (set (map :point-idx anchor-group))
                      new-samples (mapv (fn [idx width]
                                          (if (contains? idx-set idx)
                                            (-> (+ width delta)
                                                (max min-width)
                                                (min max-width))
                                            width))
                                        (range (count samples))
                                        samples)
                      new-path (assoc path :width-samples new-samples)]
                  [(assoc paths-acc path-id new-path)
                    (into aabb-anchors-acc anchor-group)])

                :curve
                (throw (ex-info "Adjusting width on curve-controlled path not yet supported"
                                {:path-id path-id :width-type width-type})))))
          [paths #{}]
          (group-by :path-id anchors))]
    {:paths new-paths
     :aabb (aabb paths new-paths aabb-anchors)}))


(defn- extrude-curve
  "执行曲线挤出操作，返回 [new-curve, new-t-params]。"
  [^Curve old-curve old-t-params point is-start?]
  (let [new-curve (Curve.)
        new-t-params (if is-start?
                       (CurveExtrusionUtils/extrudeHead
                         old-curve (double-array old-t-params)
                         (Pair. (:x point) (:y point))
                         new-curve)
                       (CurveExtrusionUtils/extrudeTail
                         old-curve (double-array old-t-params)
                         (Pair. (:x point) (:y point))
                         new-curve))
        arc-params (TableMapping/uniformSParams
                     (ArcLengthUtils/buildArcLengthParams new-curve new-t-params))]
    {:curve new-curve
     :t-params (vec new-t-params)
     :arc-params (vec arc-params)}))

(defn active-anchor-after-extrude
  "挤出后，计算原锚点的新索引。起点挤出时索引从 0 变为 1；终点挤出时索引不变。"
  [anchor is-start?]
  (if is-start?
    (->Anchor (:path-id anchor) 1)
    anchor))

(defn extrude-anchor
  "根据锚点挤出路径。如果锚点是首尾点，则在对应端挤出；否则返回 nil。
   返回 {:paths new-paths :aabb aabb :anchor updated-anchor :new-anchor new-anchor}。"
  [paths ^Anchor anchor point]
  (when (end-anchor? paths anchor)
    (let [path-id (:path-id anchor)
          path (get paths path-id)
          curve-edn (:bezier-curve path)
          idx (:point-idx anchor)
          is-start? (zero? idx)
          width-type (path/path-width-type path)
          old-curve (bezier/edn->curve curve-edn)
          old-t-params (or (:t-params path) (path/point-t-params path))

          ;; 挤出操作，一次性获得所有必要数据
          {:keys [curve t-params arc-params]} (extrude-curve old-curve old-t-params point is-start?)
          new-points (vec (.getPoints curve))
          new-num-points (count new-points)
          new-curve-edn (bezier/curve->edn curve)

          ;; 根据宽度类型构建新路径
          new-path
          (case width-type
            :fixed
            (-> path
                (assoc :bezier-curve new-curve-edn)
                (dissoc :width-samples :arc-params :t-params))

            :point-width
            (let [old-samples (:width-samples path)
                  width (if is-start? (first old-samples) (last old-samples))
                  new-samples (if is-start?
                                (into [width] old-samples)
                                (into old-samples [width]))]
              (-> path
                  (assoc :bezier-curve new-curve-edn)
                  (assoc :width-samples (vec new-samples))
                  (assoc :arc-params arc-params)
                  (dissoc :t-params)))

            :t-width
            (let [old-samples (:width-samples path)
                  width (if is-start? (first old-samples) (last old-samples))
                  new-samples (if is-start?
                                (into [width] old-samples)
                                (into old-samples [width]))]
              (-> path
                  (assoc :bezier-curve new-curve-edn)
                  (assoc :width-samples (vec new-samples))
                  (assoc :t-params t-params)
                  (assoc :arc-params arc-params)))

            :curve
            (throw (ex-info "Curve width control not yet supported for extrusion"
                            {:path-id path-id :width-type width-type})))

          new-paths (assoc paths path-id new-path)
          new-anchor (if is-start?
                       (->Anchor path-id 0)
                       (->Anchor path-id (dec new-num-points)))
          updated-anchor (active-anchor-after-extrude anchor is-start?)]
      {:paths new-paths
       :aabb (aabb new-paths [new-anchor])
       :anchor updated-anchor
       :new-anchor new-anchor})))
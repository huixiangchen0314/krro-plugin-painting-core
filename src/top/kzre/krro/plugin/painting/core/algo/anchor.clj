(ns top.kzre.krro.plugin.painting.core.algo.anchor
  (:require
   [top.kzre.krro.curve.bezier2d.core :as bezier])
  (:import
    (top.kzre.curve.bezier2d
      ArcLengthUtils ChordLengthTable
      Curve
      CurveExtrusionUtils
      Pair TableMapping)))

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

(defn path-width-type
  "路径宽度控制类型。根据路径中的宽度控制字段确定宽度控制方式。

  返回值为以下四种类型之一：

  - :fixed     固定宽度，整个路径宽度恒定，使用 :width 或默认值。
                对应数据：无 :width-samples，无 :t-params，无 :width-curve。

  - :point-width 控制点宽度，宽度值存储在每个控制点上（宽度采样数量等于控制点数量）。
                对应数据：有 :width-samples，无 :t-params，无 :width-curve。
                宽度通过控制点索引直接索引。

  - :t-width   参数化宽度，宽度采样与参数 t（0~1）关联，采样点数独立于控制点数量。
                对应数据：有 :width-samples，有 :t-params，无 :width-curve。
                宽度通过 t 参数线性插值，适用于宽度变化复杂但路径简单的场景。

  - :curve     曲线宽度控制，使用显式的宽度曲线函数（如样条或高阶插值）。
                对应数据：有 :width-curve。
                提供最灵活的宽度控制，但需要额外计算开销。

  判断优先级：:width-curve > :width-samples > 默认 :fixed。

  示例：
    (path-width-type path) ; => :fixed | :point-width | :t-width | :curve"
  [path]
  (cond
    (:width-curve path) :curve
    (seq (:width-samples path))
    (if (seq (:t-params path))
      :t-width
      :point-width)
    :else :fixed))

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


;; 辅助：构造均匀 t 参数（与控制点数量一致）
(defn- uniform-t-params
  "生成均匀参数 t = i / (n-1)。"
  [num-points]
  (when (> num-points 1)
    (mapv #(/ % (dec num-points)) (range num-points))))

(defn- point-t-params [path]
  (let [num-points (count (get-in path [:bezier-curve :points]))]
    (uniform-t-params num-points)))


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

(defn ensure-width-type*
  "确保路径具有指定的宽度类型。可选参数可为 delay 或其他值。
   如果参数是 delay，则只在需要时 deref。"
  [path width-type & {:keys [t-params width-samples arc-params width-curve compute-arc?]
                      :or {compute-arc? false}}]
  (let [num-points (count (get-in path [:bezier-curve :points]))
        default-width (get-in path [:style :stroke :width] 1.0)
        curve-edn (:bezier-curve path)]
    (case width-type
      :fixed
      (if (get-in path [:style :stroke :width])
        path
        (-> path
            (dissoc :width-samples :t-params :arc-params :width-curve)
            (assoc-in [:style :stroke :width] default-width)
            (assoc :width-type :fixed)))

      :point-width
      (let [samples (or (:width-samples path)
                        (if (delay? width-samples) @width-samples width-samples)
                        (vec (repeat num-points default-width)))
            _ (when (not= (count samples) num-points)
                (throw (ex-info "width-samples length must equal control points"
                                {:expected num-points :actual (count samples)})))
            arc (or (:arc-params path)
                    (if (delay? arc-params) @arc-params arc-params)
                    (when compute-arc?
                      (let [curve (bezier/edn->curve curve-edn)
                            t (or (if (delay? t-params) @t-params t-params)
                                  (uniform-t-params num-points))
                            arc-lengths (ArcLengthUtils/buildArcLengthParams curve (double-array t))]
                        (vec (TableMapping/uniformSParams arc-lengths)))))]
        (-> path
            (dissoc :t-params :width-curve)
            (assoc :width-samples samples)
            (assoc :arc-params arc)
            (assoc :width-type :point-width)))

      :t-width
      (let [t-params (or (:t-params path)
                         (if (delay? t-params) @t-params t-params)
                         (uniform-t-params num-points))
            _ (when (not= (count t-params) num-points)
                (throw (ex-info "t-params length must equal control points"
                                {:expected num-points :actual (count t-params)})))
            samples (or (:width-samples path)
                        (if (delay? width-samples) @width-samples width-samples)
                        (vec (repeat num-points default-width)))
            _ (when (not= (count samples) num-points)
                (throw (ex-info "width-samples length must equal t-params"
                                {:expected (count t-params) :actual (count samples)})))
            arc (or (:arc-params path)
                    (when compute-arc?
                      (if (delay? arc-params) @arc-params arc-params)
                      (let [curve (bezier/edn->curve curve-edn)
                            arc-lengths (ArcLengthUtils/buildArcLengthParams curve (double-array t-params))]
                        (vec (TableMapping/uniformSParams arc-lengths)))))]
        (-> path
            (dissoc :width-curve)
            (assoc :t-params t-params)
            (assoc :width-samples samples)
            (assoc :arc-params arc)
            (assoc :width-type :t-width)))
      :curve
      (throw (ex-info "Curve width type not yet supported" {:path path})))))


(defmacro ensure-width-type
  "确保路径具有指定的宽度类型。可选参数被包装为 delay，只在需要时求值，避免浪费计算。"
  [path width-type & {:keys [t-params width-samples arc-params width-curve compute-arc?]}]
  (let [wrap-delay (fn [expr]
                     (if (some? expr)
                       `(delay ~expr)
                       nil))]
    `(ensure-width-type* ~path ~width-type
                         :t-params ~(wrap-delay t-params)
                         :width-samples ~(wrap-delay width-samples)
                         :arc-params ~(wrap-delay arc-params)
                         :width-curve ~(wrap-delay width-curve)
                         :compute-arc? ~(if (some? compute-arc?) compute-arc? true))))



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
                  width-type (path-width-type path)]
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
                   (into aabb-anchors-acc (anchors path))])

                (:point-width :t-width)
                (let [path (ensure-width-type path width-type)
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
          width-type (path-width-type path)
          old-curve (bezier/edn->curve curve-edn)
          old-t-params (or (:t-params path) (point-t-params path))

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
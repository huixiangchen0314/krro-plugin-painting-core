(ns top.kzre.krro.plugin.painting.core.algo.path
  "路径相关逻辑，是矢量逻辑的基础"
  (:require
   [top.kzre.krro.curve.bezier2d.core :as bezier])
  (:import
    (top.kzre.curve.bezier2d ArcLengthUtils TableMapping)))

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

(defn uniform-t-params
  "生成均匀参数 t = i / (n-1)。"
  [num-points]
  (when (> num-points 1)
    (mapv #(/ % (dec num-points)) (range num-points))))


(defn point-t-params [path]
  (let [num-points (count (get-in path [:bezier-curve :points]))]
    (uniform-t-params num-points)))


(defn ensure-width-type*
  "确保路径具有指定的宽度类型。可选参数可为 delay 或其他值。
   如果参数是 delay，则只在需要时 deref。"
  [path width-type & {:keys [t-params width-samples arc-params _width-curve compute-arc?]
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


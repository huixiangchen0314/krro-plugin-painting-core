(ns top.kzre.krro.plugin.painting.core.tool.stroke
  "笔触工具命名空间，负责构建 Stroke 装饰器链，供画笔和矢量笔刷工具复用。
   提供：
     - create-stroke-chain : 根据笔刷规格构建 [ResampleStroke, DynamicsStroke] 链。
     - ->pointer-event     : 将工具内部的 Clojure map 事件转换为 PointerEvent 对象。"
  (:require
    [top.kzre.krro.plugin.painting.core.brush.core :as brush])
  (:import
    (top.kzre.krro.brush AbstractStroke DefaultStroke PointerEvent PointerEvent$EventType ReducedStroke
                         SmoothStroke SpacingFunction)))

(defn ->pointer-event
  "将本地事件 map 转换为 PointerEvent。
   期望的键：:x :y :pressure :tilt-x :tilt-y :rotation :timestamp :type
   :type 值应为 :press / :drag / :release，映射为 DOWN / MOVE / UP。"
  [ev]
  (-> (PointerEvent/newBuilder)
      (.x (float (:x ev)))
      (.y (float (:y ev)))
      (.pressure (float (:pressure ev 1)))
      (.tiltX (float (:tilt-x ev 0)))
      (.tiltY (float (:tilt-y ev 0)))
      (.rotation (float (:rotation ev 0)))
      (.timestamp (long (:timestamp ev (System/currentTimeMillis))))
      (.type (case (:type ev)
               :press   PointerEvent$EventType/DOWN
               :drag    PointerEvent$EventType/MOVE
               :release PointerEvent$EventType/UP
               PointerEvent$EventType/MOVE))
      (.build)))

(defn make-stroke
  "根据笔刷规格构建完整的 Stroke 装饰器链。
   返回 map：
     :stroke   - 最外层的 ResampleStroke，用于提取等距采样点。
   笔刷规格可选键：
     :smooth  - 平滑因子 (0.0 ~ 1.0)，不提供则不平滑。
     :reduce  - 降采样阈值（像素），不提供则不降采样。
     :spacing - 间距系数（相对于半径），默认 0.2。"
  ([]
   (make-stroke @brush/global-brush))
  ([brush-spec]
   (let [raw   (if-let [threshold (:reduce brush-spec)]
                     (ReducedStroke/newInstance (float threshold) brush-spec)
                     (DefaultStroke/create brush-spec))
         ^AbstractStroke smooth (if-let [alpha (:smooth brush-spec)]
                     (SmoothStroke/cable raw (float alpha))
                     raw)
         spacing-fn (reify SpacingFunction
                      (getStep [_ prev _current]
                        (let [e (.getStrokeEvent smooth prev)
                              radius      (float (get e :radius 10.0))
                              spacing     (float (get brush-spec :spacing 0.2))]
                          (* 2.0 radius spacing))))
         resampled (SmoothStroke/resample smooth spacing-fn)]
     resampled)))
(ns top.kzre.krro.plugin.painting.core.tool.stroke
  "笔触工具命名空间，负责构建 Stroke 装饰器链，供画笔和矢量笔刷工具复用。
   提供：
     - create-stroke-chain : 根据笔刷规格构建 [ResampleStroke, DynamicsStroke] 链。
     - ->pointer-event     : 将工具内部的 Clojure map 事件转换为 PointerEvent 对象。"
  (:require
    [top.kzre.krro.plugin.painting.core.brush.core :as brush])
  (:import
    (top.kzre.krro.brush DefaultStroke SmoothStroke ReducedStroke
                         DynamicsStroke DynamicsMapper Stroke
                         SpacingFunction PointerEvent PointerEvent$EventType)))

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

(defn default-stroke
  "根据笔刷规格构建完整的 Stroke 装饰器链。
   返回 map：
     :stroke   - 最外层的 ResampleStroke，用于提取等距采样点。
     :dynamics - DynamicsStroke，用于获取每个事件的动力学参数。
   笔刷规格可选键：
     :smooth  - 平滑因子 (0.0 ~ 1.0)，不提供则不平滑。
     :reduce  - 降采样阈值（像素），不提供则不降采样。
     :spacing - 间距系数（相对于半径），默认 0.2。"
  ([]
   (default-stroke @brush/global-brush))
  ([brush-spec]
   (let [raw       (DefaultStroke/create)
         smooth    (if-let [alpha (:smooth brush-spec)]
                     (SmoothStroke/cable raw (float alpha))
                     raw)
         reduced   (if-let [threshold (:reduce brush-spec)]
                     (ReducedStroke/fromStroke smooth (float threshold))
                     smooth)
         dyn       (DynamicsStroke. reduced (DynamicsMapper/instance) brush-spec)
         spacing-fn (reify SpacingFunction
                      (getStep [_ prev _current]
                        (let [prev-params (.getParams dyn prev)
                              radius      (float (get prev-params :radius 10.0))
                              spacing     (float (get brush-spec :spacing 0.2))]
                          (* 2.0 radius spacing))))
         resampled (SmoothStroke/resample dyn spacing-fn)]
     {:stroke resampled, :dynamics dyn})))
(ns top.kzre.krro.plugin.painting.core.tool.vector-brush
  "矢量画笔工具：将指针事件转换为矢量路径，追加到矢量图层的路径集合中。
   遵循 ITool 协议，提交时生成贝塞尔曲线及宽度采样，并返回新图层。"
  (:require
    [top.kzre.krro.brush.vector :as vec-brush]
    [top.kzre.krro.canvas.core.layer.util :as layer-util]
    [top.kzre.krro.curve.bezier2d.core :as bezier]
    [top.kzre.krro.plugin.painting.core.brush.core  :as brush]
    [top.kzre.krro.plugin.painting.core.state]
    [top.kzre.krro.plugin.painting.core.tool.protocol :as tp]
    [top.kzre.krro.plugin.painting.core.tool.util :as tool-util])
  (:import
    (top.kzre.colorutils.color RGB)))

(defrecord VectorBrushTool [events        ;; atom: 累积的事件向量
                            brush         ;; 当前笔刷规格
                            parent-inv]   ;; atom: 缓存父逆矩阵
  tp/ITool
  (id [_] :vector-brush)
  (overlay [_] nil)
  (begin! [_ layer state ctx]
    (reset! events [])
    (reset! parent-inv nil)
    {:layer layer :state state})

  (end! [_ layer state ctx]
    (reset! events [])
    (reset! parent-inv nil)
    {:layer layer :state state})

  (apply! [_ layer _state ev ctx]
    (let [local-ev (if-let [inv-matrix @parent-inv]
                     (let [pt (layer-util/transform-point inv-matrix (:x ev) (:y ev))]
                       (assoc ev :x (:x pt) :y (:y pt)))
                     (let [layers (:layers (:data ctx))
                           layer-path (layer-util/find-layer-path (:id layer) layers)
                           total-inv (tool-util/compute-total-inverse layer layers layer-path)]
                       (reset! parent-inv total-inv)
                       (let [pt (layer-util/transform-point total-inv (:x ev) (:y ev))]
                         (assoc ev :x (:x pt) :y (:y pt)))))]
      (case (:type local-ev)
        :press   (do (reset! events []) :start)
        :drag    (do (swap! events conj local-ev) :continue)
        :release (do (swap! events conj local-ev) :commit)
        :idle)))

  (preview! [_ layer state ctx]
    {:layer layer :state state})

  (commit! [_ layer state ctx]
    (let [evs @events]
      (if (seq evs)
        (let [result (vec-brush/generate-vector-stroke brush evs)
              curve  (:curve result)
              valid? (try
                       (when (and curve (pos? (-> curve bezier/curve->edn :points count)))
                         true)
                       (catch Exception _ false))]
          (if valid?
            (let [path-id  (keyword (str "path-" (System/currentTimeMillis)))
                  curve-edn (bezier/curve->edn curve)
                  new-path {:path-type :bezier
                            :bezier-curve curve-edn
                            :style {:stroke {:color (RGB/rgba 0 0 0 1) :width 5 :cap :round :join :round}}
                            :width-samples (:width-samples result)
                            :arc-params (:arc-params result)}
                  new-layer (-> layer
                                (assoc-in [:paths-map path-id] new-path)
                                (update :path-order conj path-id))]
              {:layer new-layer
               :state (assoc state :dirty-tiles nil)})
            {:layer layer :state state}))
        {:layer layer :state state}))))

(defn make-vector-brush []
  (->VectorBrushTool (atom []) (or @brush/global-brush brush/default-brush) (atom nil)))
(ns top.kzre.krro.plugin.painting.core.tool.vector-brush
  "矢量画笔工具：将指针事件转换为矢量路径，追加到矢量图层的路径集合中。
   遵循 ITool 协议，提交时生成贝塞尔曲线及宽度采样，并返回新图层。"
  (:require
    [top.kzre.krro.brush.vector :as vec-brush]
    [top.kzre.krro.curve.bezier2d.core :as bezier]
    [top.kzre.krro.plugin.painting.core.brush.core  :as brush]
    [top.kzre.krro.plugin.painting.core.state]
    [top.kzre.krro.plugin.painting.core.tool.protocol :as tp])
  (:import
    (top.kzre.colorutils.color RGB)))

(defrecord VectorBrushTool [events        ;; atom: 累积的事件向量
                            brush]        ;; 当前笔刷规格
  tp/ITool
  (begin! [_ layer state ctx]
    (reset! events [])
    {:layer layer :state state})

  (end! [_ layer state ctx]
    {:layer layer :state state})

  (apply! [_ layer _state ev ctx]
    (case (:type ev)
      :press   (do (reset! events []) :start)
      :drag    (do (swap! events conj ev) :continue)
      :release (do (swap! events conj ev) :commit)
      :idle))

  (preview! [_ layer state ctx]
    ;; 待实现实时预览，目前保持原图层
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
              ;; 设为 nil 表示全图刷新
              {:layer new-layer
               :state (assoc state :dirty-tiles nil)})
            {:layer layer :state state}))
        {:layer layer :state state}))))

(defn make-vector-brush []
  (->VectorBrushTool (atom []) (or @brush/global-brush brush/default-brush)))
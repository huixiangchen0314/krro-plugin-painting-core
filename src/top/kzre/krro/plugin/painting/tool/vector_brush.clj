(ns top.kzre.krro.plugin.painting.tool.vector-brush
  "矢量画笔工具：将指针事件转换为矢量路径，追加到矢量图层的路径集合中。
   遵循 ITool 协议，提交时生成贝塞尔曲线及宽度采样，并返回新图层。"
  (:require
   [top.kzre.krro.brush.vector :as vec-brush] ;; 用于获取笔刷默认值
   [top.kzre.krro.curve.bezier2d.core :as bezier]
   [top.kzre.krro.plugin.painting.canvas.brush :as brush]
   [top.kzre.krro.plugin.painting.tool.protocol :as tp])
  (:import
   [top.kzre.colorutils.color RGB]))     ;; EDN 转换

(defrecord VectorBrushTool [events        ;; atom: 累积的事件向量
                            brush]        ;; 当前笔刷规格
  tp/ITool
  (begin! [this layer ctx] layer)
  (end! [this layer ctx] layer)

  (apply! [this layer ev ctx]
    (case (:type ev)
      :press   (do (reset! events []) :start)
      :drag    (do (swap! events conj ev) :continue)
      :release (do (swap! events conj ev) :commit)
      :idle))

  (preview! [this layer ctx] layer)   ;; 待实现实时预览

  (commit! [this layer ctx]
    (let [evs @events]
      (when (seq evs)
        (let [;; 生成矢量笔触
              result   (vec-brush/generate-vector-stroke brush evs)
              curve    (:curve result)
              ;; 验证曲线有效（至少2个控制点）
              valid?   (try
                         (when (and curve (pos? (-> curve bezier/curve->edn :points count)))
                           true)
                         (catch Exception _ false))]
          (if valid?
            (let [;; 构造新路径的 ID
                  path-id  (keyword (str "path-" (System/currentTimeMillis)))
                  ;; 关键：将 Java Curve 转为 EDN 存储
                  curve-edn (bezier/curve->edn curve)
                  new-path {:path-type :bezier
                            :bezier-curve curve-edn    ;; 存储 EDN
                            :style {:stroke {:color (RGB/rgba 0 0 0 1) :width 5 :cap :round :join :round}}
                            :width-samples (:width-samples result)
                            :arc-params (:arc-params result)}
                  ;; 追加到图层
                  new-layer (-> layer
                                (assoc-in [:paths-map path-id] new-path)
                                (update :path-order conj path-id))]
              new-layer)
            layer))))))

(defn make-vector-brush []
  (->VectorBrushTool (atom []) (or @brush/global-brush brush/default-brush)))
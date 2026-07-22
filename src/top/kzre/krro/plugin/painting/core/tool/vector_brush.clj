(ns top.kzre.krro.plugin.painting.core.tool.vector-brush
  "矢量画笔工具：将指针事件转换为矢量路径，追加到矢量图层的路径集合中。
   不管理备份，直接从 runtime 中读取备份图层。动作使用 :commit 返回更新后的图层。
   提交后自动更新备份图层，以便连续绘制。"
  (:require
    [top.kzre.krro.brush.vector :as vec-brush]
    [top.kzre.krro.canvas.core.layer.util :as layer-util]
    [top.kzre.krro.curve.bezier2d.core :as bezier]
    [top.kzre.krro.plugin.painting.core.brush.core :as brush]
    [top.kzre.krro.plugin.painting.core.tool.protocol :as tp]
    [top.kzre.krro.plugin.painting.core.tool.util :as tool-util])
  (:import
    (top.kzre.colorutils.color RGB)))

;; ── 内部辅助：基于备份图层和路径数据生成新图层 ──
(defn- add-path-to-layer
  "向备份图层添加一个路径，返回新图层。path-id 和 extra-attrs 可定制。"
  [backup-layer path-data path-id]
  (let [curve-edn (bezier/curve->edn (:curve path-data))
        new-path {:path-type :bezier
                  :bezier-curve curve-edn
                  :style {:stroke {:color (RGB/rgba 0 0 0 1)
                                   :width 5
                                   :cap :round
                                   :join :round}}
                  :width-samples (:width-samples path-data)
                  :arc-params (:arc-params path-data)}]
    (-> backup-layer
        (assoc-in [:paths-map path-id] new-path)
        (update :path-order conj path-id))))

;; ═══════════════════════════════════════════════════════
;; 工具实现
;; ═══════════════════════════════════════════════════════
(defrecord VectorBrushTool [events        ;; atom: 累积的事件向量
                            brush         ;; 当前笔刷规格
                            parent-inv]   ;; atom: 缓存父逆矩阵
  tp/ITool
  (id [_] :vector-brush)
  (overlay [_] nil)

  (begin! [_ layer rt _ctx]
    (reset! events [])
    (reset! parent-inv nil)
    {:layer layer :state rt})

  (end! [_ layer rt _ctx]
    (reset! events [])
    (reset! parent-inv nil)
    {:layer layer :state rt})

  (apply! [_ layer _rt ev ctx]
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
        :release :commit    ; 直接提交，工具只返回图层，外部负责修改项目
        :idle)))

  (preview! [_ layer rt ctx]
    (let [evs @events]
      (if (seq evs)
        (let [result (vec-brush/generate-vector-stroke brush evs)
              curve  (:curve result)
              valid? (and curve (pos? (-> curve bezier/curve->edn :points count)))]
          (if valid?
            (let [backup-layer (:layer-backup rt)
                  preview-id (keyword (str "preview-" (System/currentTimeMillis)))
                  new-layer (add-path-to-layer backup-layer result preview-id)]
              {:layer new-layer :state (assoc rt :dirty-tiles nil)})
            {:layer layer :state rt}))
        {:layer layer :state rt})))

  (commit! [_ layer rt ctx]
    (let [evs @events]
      (if (seq evs)
        (let [result (vec-brush/generate-vector-stroke brush evs)
              curve  (:curve result)
              valid? (and curve (pos? (-> curve bezier/curve->edn :points count)))]
          (if valid?
            (let [backup-layer (:layer-backup rt)
                  path-id  (keyword (str "path-" (System/currentTimeMillis)))
                  new-layer (add-path-to-layer backup-layer result path-id)]
              (reset! events [])
              (reset! parent-inv nil)
              ;; 提交后更新备份为当前图层，以便连续绘制
              {:layer new-layer
               :state (assoc rt :layer-backup new-layer
                                :dirty-tiles nil)})
            {:layer layer :state rt}))
        {:layer layer :state rt}))))

(defn make-vector-brush []
  (->VectorBrushTool (atom []) (or @brush/global-brush brush/default-brush) (atom nil)))
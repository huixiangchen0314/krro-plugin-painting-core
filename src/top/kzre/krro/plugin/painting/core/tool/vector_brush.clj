(ns top.kzre.krro.plugin.painting.core.tool.vector-brush
  "矢量画笔工具：将指针事件转换为矢量路径，追加到矢量图层的路径集合中。
   使用 Stroke 装饰器链预处理事件，再拟合贝塞尔曲线。
   提交后自动更新备份图层，以便连续绘制。"
  (:require
    [top.kzre.krro.brush.vector :as vec-brush]
    [top.kzre.krro.curve.bezier2d.core :as bezier]
    [top.kzre.krro.plugin.painting.core.brush.core :as brush]
    [top.kzre.krro.plugin.painting.core.tool.protocol :as tp]
    [top.kzre.krro.plugin.painting.core.tool.stroke :as stroke]
    [top.kzre.krro.plugin.painting.core.tool.util :as tool-util])
  (:import
    (top.kzre.colorutils.color RGB)
    (top.kzre.krro.brush Stroke)))

;; ── 内部辅助：基于备份图层和路径数据生成新图层 ──
(defn- add-path-to-layer
  [backup-layer path-data path-id]
  (let [curve-edn (bezier/curve->edn (:curve path-data))
        new-path {:path-type :bezier
                  :bezier-curve curve-edn
                  :style {:stroke {:color (RGB/rgba 0.6 0 0 1)
                                   :width 50
                                   :cap :round
                                   :join :square}}
                  :width-samples (:width-samples path-data)
                  :arc-params (:arc-params path-data)}]
    (-> backup-layer
        (assoc-in [:paths path-id] new-path)
        (update :path-order conj path-id))))

;; ═══════════════════════════════════════════════════════
;; 工具实现
;; ═══════════════════════════════════════════════════════
(defrecord VectorBrushTool [stroke-atom    ;; atom: 最外层 Stroke (ResampleStroke)
                            brush          ;; 当前笔刷规格
                            parent-inv]    ;; atom: 缓存父逆矩阵
  tp/ITool
  (id [_] :vector-brush)
  (overlay [_] nil)

  (begin! [_ layer rt _ctx]
    (reset! stroke-atom nil)
    (reset! parent-inv nil)
    {:layer layer :state rt})

  (end! [_ layer rt _ctx]
    (reset! stroke-atom nil)
    (reset! parent-inv nil)
    {:layer layer :state rt})

  (apply! [_ layer _rt ev ctx]
    (let [{:keys [event parent-inv-new]} (tool-util/transform-event ev layer (:data ctx) :parent-inv @parent-inv)]
      (reset! parent-inv parent-inv-new)
      (let [pevent (stroke/->pointer-event event)
            current-stroke @stroke-atom
            new-stroke (cond
                         (= :press (:type event))
                         (stroke/make-stroke brush)   ; 创建完整链（已包含初始事件）
                         (#{:drag :release} (:type event))  ; 注意：release 也可作为最终事件追加，但通常 commit 时取完整链
                         (if current-stroke
                           (.append ^Stroke current-stroke pevent)
                           nil)
                         :else
                         current-stroke)]   ; 其他事件（如 idle）不处理
        (when new-stroke
          (reset! stroke-atom new-stroke))
        (case (:type event)
          :press   :start
          :drag    :continue
          :release :commit
          :idle))))

  (preview! [_ layer rt ctx]
    (if-let [^Stroke stroke @stroke-atom]
      (let [stroke-vec (.getStroke stroke)]   ; IPersistentVector of maps
        (if (> (count stroke-vec) 1)
          (if-let [result (vec-brush/render-vector-stroke stroke-vec)]
            (let [backup-layer (:layer-backup rt)
                  preview-id (keyword (str "preview-" (System/currentTimeMillis)))
                  new-layer (add-path-to-layer backup-layer result preview-id)]
              {:layer new-layer :state (assoc rt :dirty-tiles nil)})
            {:layer layer :state rt})
          {:layer layer :state rt}))
      {:layer layer :state rt}))

  (commit! [_ layer state ctx]
    (if-let [^Stroke stroke @stroke-atom]
      (let [stroke-vec (.getStroke stroke)]
        (if (> (count stroke-vec) 1)
          (if-let [result (vec-brush/render-vector-stroke stroke-vec)]
            (let [backup-layer (:layer-backup state)
                  path-id  (keyword (str "path-" (System/currentTimeMillis)))
                  new-layer (add-path-to-layer backup-layer result path-id)]
              ;; 完成一笔，重置链
              (reset! stroke-atom nil)
              (reset! parent-inv nil)
              {:layer new-layer
               :state (-> state
                          (assoc :layer-backup new-layer)
                          (assoc :dirty-tiles nil))})
            {:layer layer :state state})
          {:layer layer :state state}))
      {:layer layer :state state})))

(defn make-vector-brush []
  (->VectorBrushTool (atom nil)
                     (or @brush/global-brush brush/default-brush)
                     (atom nil)))
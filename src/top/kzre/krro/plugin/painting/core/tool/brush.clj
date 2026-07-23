(ns top.kzre.krro.plugin.painting.core.tool.brush
  "画笔工具：完全基于 TiledCanvas。
   事件处理使用新的 Stroke 装饰器链（平滑、降采样、动力学映射）。
   渲染逻辑保持不变，复用 render-stroke-dirties! 和 render-dab!。"
  (:require
   [top.kzre.krro.brush.core :as brush-core]
   [top.kzre.krro.canvas.core.layer.util :as layer-util]
   [top.kzre.krro.core.custom :as custom]
   [top.kzre.krro.plugin.painting.core.brush.core :as brush]
   [top.kzre.krro.plugin.painting.core.ops.layer :as layer]
   [top.kzre.krro.plugin.painting.core.ops.undo :as undo]
   [top.kzre.krro.plugin.painting.core.tool.protocol :as tp]
   [top.kzre.krro.plugin.painting.core.tool.stroke :as stroke]
   [top.kzre.krro.plugin.painting.core.tool.util :as tool-util]
   [top.kzre.krro.plugin.undo.protocol])
  (:import
   (top.kzre.krro.brush
    DefaultStroke
    DynamicsMapper
    DynamicsStroke
    ReducedStroke
    SmoothStroke
    SpacingFunction
    Stroke)
   (top.kzre.krro.util.tile TiledCanvas)))

;; ── 自定义配置 ──────────────────────────────────
(custom/defcustom :krro.painting/brush-max-preview-events
                  64
                  :type :integer
                  :group :krro.painting/performance
                  :doc "预览时处理的最大事件数。")

;; ── 笔刷配置 ──────────────────────────────────
(defn- get-brush []
  (or @brush/global-brush brush/default-brush))

(defn- create-stroke-chain [brush-spec]
  (let [raw    (DefaultStroke/create)
        smooth (if-let [alpha (:smooth brush-spec)]
                 (SmoothStroke/cable raw (float alpha))
                 raw)
        reduced (if-let [threshold (:reduce brush-spec)]
                  (ReducedStroke/fromStroke smooth (float threshold))
                  smooth)
        dyn     (DynamicsStroke. reduced (DynamicsMapper/instance) brush-spec)
        spacing-fn (reify SpacingFunction
                     (getStep [_ prev current]
                       (let [prev-params (.getParams dyn prev)
                             radius      (float (get prev-params :radius 10.0))
                             spacing     (float (get brush-spec :spacing 0.2))]
                         (* 2.0 radius spacing))))
        resampled (SmoothStroke/resample dyn spacing-fn)]
    [resampled dyn]))

;; ═══════════════════════════════════════════════════════
;; 工具实现（符合新协议）
;; ═══════════════════════════════════════════════════════
(defrecord BrushTool [stroke-atom    ;; atom: 当前笔触链（最终为 DynamicsStroke）
                      dynamics-atom
                      parent-inv
                      current-pos
                      ]
  tp/ITool
  (id [_] :brush)
  (overlay [_] {:type :circle, :radius 10})

  (begin! [_ layer rt _ctx]
    (reset! parent-inv nil)
    {:layer layer :state rt})

  (end! [_ layer rt _ctx]
    (reset! parent-inv nil)
    {:layer layer :state rt})

  (apply! [_ layer _rt ev ctx]
    (reset! current-pos {:x (:x ev) :y (:y ev)})
    ;; 坐标变换（局部变量 local-ev 构建逻辑保持不变）
    ;; ─── 画笔工具 apply! 片段 ───
    (let [{:keys [event parent-inv-new]} (tool-util/transform-event ev layer (:data ctx) :parent-inv @parent-inv)]
      (reset! parent-inv parent-inv-new)
      (let [pevent (stroke/->pointer-event event)]
        (when (= :press (:type event))
          (let [{:keys [stroke dynamics]} (stroke/default-stroke (get-brush))]
            (reset! stroke-atom stroke)
            (reset! dynamics-atom dynamics)))
        (when (#{:press :drag :release} (:type event))
          (.push ^Stroke @stroke-atom pevent))
        (case (:type event)
          :press   :start
          :drag    :continue
          :release :no-replace
          :move    :update
          :hover   :update))))

  (preview! [_ layer rt ctx]
    (when-let [^DynamicsStroke dyn @dynamics-atom]
      (let [max-events (custom/get-custom :krro.painting/brush-max-preview-events (:frame ctx))]
        (if (> (.size dyn) 0)
          (let [params-vec  (.getParamsVector dyn)
                tail-params (if (> (count params-vec) max-events)
                              (subvec params-vec (- (count params-vec) max-events))
                              params-vec)
                brush-spec  (get-brush)
                [new-canvas dirties] (brush-core/render-stroke-dirties!
                                       (:canvas layer)
                                       {:brush brush-spec :params tail-params})]
            {:layer (assoc layer :canvas new-canvas)
             :state (assoc rt :dirty-tiles (into (or (:dirty-tiles rt) #{}) dirties))})
          {:layer layer :state rt}))))

  (commit! [_ layer rt ctx]
    (when-let [^DynamicsStroke dyn @dynamics-atom]
      (if (> (.size dyn) 0)
        (let [params-vec   (.getParamsVector dyn)
              brush-spec   (get-brush)
              stroke-data  {:brush brush-spec :params params-vec}
              layer-canvas (:canvas layer)
              backup-canvas (:canvas (:layer-backup rt))
              ^TiledCanvas tmp-canvas
              (doto (TiledCanvas. (.getTileSize backup-canvas)
                                  (.getDefaultPixel backup-canvas))
                (.shareFrom backup-canvas))
              [new-canvas dirties] (brush-core/render-stroke-dirties!
                                     backup-canvas stroke-data)
              updated-canvas (.mergeCanvas layer-canvas new-canvas)
              merged-dirties (into (or (:dirty-tiles rt) #{}) dirties)
              new-layer (assoc layer :canvas updated-canvas)]
          (layer/replace-layer! (:canvas-id ctx) new-layer)
          (undo/record-raster-stroke! (:canvas-id ctx) (:id layer)
                                      tmp-canvas updated-canvas dirties)
          (.clear tmp-canvas)
          (reset! stroke-atom nil)
          (reset! dynamics-atom nil)
          {:layer new-layer
           :state (assoc rt
                    :layer-backup {:type :raster :canvas new-canvas}
                    :dirty-tiles merged-dirties)})
        {:layer layer :state rt}))))

(defn make-brush []
  (->BrushTool (atom nil) (atom nil) (atom nil) (atom nil)))
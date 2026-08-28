(ns top.kzre.krro.plugin.painting.core.tool.brush
  "画笔工具：完全基于 TiledCanvas。
   事件处理使用不可变 Stroke 装饰器链（平滑、降采样、动力学映射）。
   渲染逻辑保持不变，复用 render-stroke-dirties! 和 render-dab!。"
  (:require
    [top.kzre.krro.brush.core :as brush-core]
    [top.kzre.krro.core.custom :as custom]
    [top.kzre.krro.plugin.painting.core.brush.core :as brush]
    [top.kzre.krro.plugin.painting.core.ops.layer :as layer]
    [top.kzre.krro.plugin.painting.core.ops.undo :as undo]
    [top.kzre.krro.plugin.painting.core.tool.protocol :as tp]
    [top.kzre.krro.plugin.painting.core.tool.stroke :as stroke]
    [top.kzre.krro.plugin.painting.core.tool.util :as tool-util]
    [top.kzre.krro.plugin.undo.protocol])
  (:import
    (top.kzre.krro.brush Stroke)
    (top.kzre.krro.canvas.core.layer LayerUtils)
    (top.kzre.krro.plugin.painting.core.tool Util)
    (top.kzre.krro.util.math KMath)
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

(defrecord BrushToolState [^Stroke stroke   ; 不可变 Stroke 链（可能包含平滑、降采样）
                           parent-inv
                           current-pos
                           world-transform])

(defn init-state [& {:keys [pointer-pos]}]
  (map->BrushToolState
    {:stroke nil
     :parent-inv nil
     :world-transform nil
     :pointer-pos pointer-pos}))

;; ── 事件处理纯函数 ──────────────────────────
(defn- handle-event
  "纯函数：给定当前工具状态和事件上下文，返回 [新状态, 操作结果标记]。"
  [^BrushToolState state layer ev ctx]
  (let [event-type (:type ev)
        pointer-pos {:x (:x ev) :y (:y ev)}
        transform-dirty? (= :press event-type)
        old-transform-inv (when-not transform-dirty? (:parent-inv state))
        ;; 坐标变换
        {:keys [event transform-inv]}
        (tool-util/transform-event ev layer (:data ctx) :transform-inv old-transform-inv)
        pevent (stroke/->pointer-event event)
        current-stroke (:stroke state)
        final-stroke (if (= :press (:type event))
                       (stroke/make-stroke (get-brush))   ; 按下：创建完整装饰链（已包含初始事件）
                       (if current-stroke
                         (.append ^Stroke current-stroke pevent)   ; 拖拽：追加事件，返回新实例
                         nil))
        result (case (:type event)
                 :press   :start
                 :drag    :continue
                 :release :no-replace
                 :move    :update
                 :hover   :update)]
    [(assoc state
       :stroke final-stroke
       :parent-inv transform-inv
       :world-transform (if transform-dirty?
                          (or (when transform-inv (KMath/mat2dInv transform-inv))
                              (KMath/mat2dIdentity))
                          (:world-transform state))
       :pointer-pos pointer-pos)
     result]))

(defrecord BrushTool [stroke-atom    ;; atom: 当前 Stroke 链（不可变）
                      parent-inv
                      current-pos
                      state-atom]
  tp/ITool
  (id [_] :brush)
  (overlay [_]
    (let [state @state-atom
          pointer-pos (:pointer-pos state)]
      {:type :circle
       :radius 10
       :x (:x pointer-pos 0)
       :y (:y pointer-pos 0)}))

  (begin! [_ layer rt _ctx]
    (reset! parent-inv nil)
    {:layer layer :state rt})

  (end! [_ layer rt _ctx]
    (reset! parent-inv nil)
    {:layer layer :state rt})

  (apply! [_ layer _rt ev ctx]
    (let [[new-state result] (handle-event @state-atom layer ev ctx)]
      (reset! state-atom new-state)
      result))

  (preview! [_ layer rt ctx]
    (let [{:keys [stroke world-transform]} @state-atom]
      (if (and stroke (> (.size ^Stroke stroke) 0))
        (let [max-events (custom/get-custom :krro.painting/brush-max-preview-events (:frame ctx))
              stroke-vec (.getStroke ^Stroke stroke)   ; IPersistentVector of maps
              params-vec (if (> (count stroke-vec) max-events)
                           (subvec stroke-vec (- (count stroke-vec) max-events))
                           stroke-vec)
              brush-spec (get-brush)
              canvas  (:canvas layer)
              tile-size (.getTileSize canvas)
              [new-canvas dirties] (brush-core/render-stroke!
                                     canvas
                                     {:brush brush-spec :params params-vec})
              world-dirties (set (LayerUtils/transformTiles dirties tile-size world-transform))]
          {:layer (assoc layer :canvas new-canvas)
           :state (assoc rt :dirty-tiles (into (or (:dirty-tiles rt) #{}) world-dirties))})
        {:layer layer :state rt})))

  (commit! [_ layer rt ctx]
    (let [{:keys [stroke world-transform]} @state-atom]
      (if (and stroke (> (.size ^Stroke stroke) 0))
        (let [params-vec (.getStroke ^Stroke stroke)   ; 所有事件参数
              brush-spec (get-brush)
              stroke-data {:brush brush-spec :params params-vec}
              layer-canvas (:canvas layer)
              tile-size (.getTileSize layer-canvas)
              backup-canvas (:canvas (:layer-backup rt))
              ^TiledCanvas tmp-canvas
              (doto (TiledCanvas. (.getTileSize backup-canvas)
                                  (.getDefaultPixel backup-canvas))
                (.shareFrom backup-canvas))
              [new-canvas dirties] (brush-core/render-stroke-dirties!
                                     backup-canvas stroke-data)
              updated-canvas (.mergeCanvas layer-canvas new-canvas)
              world-dirties (set (Util/transformTiles dirties tile-size world-transform))
              new-layer-backup (assoc layer :canvas new-canvas)
              new-layer (assoc layer :canvas updated-canvas)]
          (layer/replace-layer! (:canvas-id ctx) new-layer)
          (undo/record-raster-stroke! (:canvas-id ctx) (:id layer)
                                      tmp-canvas updated-canvas dirties)
          (.clear tmp-canvas)
          ;; 提交后清空 stroke
          (swap! state-atom assoc :stroke nil)
          {:layer new-layer
           :state (assoc rt
                    :layer-backup new-layer-backup
                    :dirty-tiles (into (or (:dirty-tiles rt) #{}) world-dirties))})
        (do
          (swap! state-atom assoc :stroke nil)
          {:layer layer :state rt})))))

(defn make-brush []
  (->BrushTool (atom nil) (atom nil) (atom nil) (atom (init-state))))
(ns top.kzre.krro.plugin.painting.core.edit.anchor
  (:require
   [taoensso.tufte :refer [p profile]]
   [top.kzre.krro.canvas.core.layer.util :as util]
   [top.kzre.krro.core.reframe :as rf]
   [top.kzre.krro.plugin.painting.core.algo.anchor :as anchor]
   [top.kzre.krro.plugin.painting.core.edit.anchor-quadtree :as anchor-quadtree :refer [anchor-quadtree-interceptor]]
   [top.kzre.krro.plugin.painting.core.edit.common :as common]
   [top.kzre.krro.plugin.painting.core.edit.interceptors :refer [cleanup-tool-interceptor
                                                                 tool-context-interceptor]]
   [top.kzre.krro.plugin.painting.core.edit.protocol :as p]
   [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
   [top.kzre.krro.plugin.painting.core.render :as render]
   [top.kzre.krro.plugin.painting.core.store :as store]
   [top.kzre.krro.plugin.painting.core.viewport :as vp])
  (:import
   (top.kzre.colorutils.color RGB)
   (top.kzre.krro.canvas.core QuadTree QuadTree$NearestResult)
   [top.kzre.krro.util.math KMath]
   (top.kzre.krro.util.tile TiledCanvas)))

(defonce anchor-modes
         #{:translate
           :rotate
           :scale
           :segment-fit
           })

(defrecord AnchorState [mode                                ;; 操作模式
                        modal                               ;; 模态编辑
                        active-anchor                       ;; 活动的锚点
                        selected-anchors                    ;; 被选择的锚点
                        selected-paths                      ;; 被选择的路径
                        last-layer-point]
  p/IToolData
  (cleanup! [_ ctx]
    (when-let [canvas-id (get-in ctx [:coeffects :record-id])]
      (swap! anchor-quadtree/anchor-quadtrees dissoc canvas-id)))

  (overlay [_ {:keys [viewport layer layer-type layer-visible layer-transform]}]
    (when (= :vector layer-type)
      (if layer-visible
        (let [paths (:paths-map layer)
              path-order (:path-order layer [])
              selected-set (or selected-anchors #{})
              ]
          (when (seq path-order)
            (concat
              ;; --- 绘制 AABB 包围盒 + 脏瓦片网格（调试） ---
              (when-let [layer-aabb (anchor/aabb paths selected-set)]
                (let [tile-size pc/global-tile-size
                      ;; 合成变换矩阵（视口 * 图层变换），与渲染管线一致
                      combined-transform (KMath/mat2dMul (vp/viewport->mat2d viewport) layer-transform)
                      ;; 使用 dirty-region 将图层 AABB 转换为屏幕空间瓦片（不裁剪，便于观察完整覆盖）
                      screen-dirties (render/dirty-region layer-aabb combined-transform 1e6 1e6 tile-size)
                      ;; 将图层 AABB 转换到屏幕（绿色矩形）
                      canvas-min (util/transform-point layer-transform (:min-x layer-aabb) (:min-y layer-aabb))
                      screen-min (vp/logic->screen viewport (:x canvas-min) (:y canvas-min))
                      canvas-max (util/transform-point layer-transform (:max-x layer-aabb) (:max-y layer-aabb))
                      screen-max (vp/logic->screen viewport (:x canvas-max) (:y canvas-max))
                      width (- (:x screen-max) (:x screen-min))
                      height (- (:y screen-max) (:y screen-min))]
                  (concat
                    ;; 绿色包围盒
                    [[:rect {:x (:x screen-min)
                             :y (:y screen-min)
                             :width width
                             :height height
                             :stroke-color [0 1 0 1]
                             :stroke-width 1.0
                             :fill-color [0 1 0 0.1]}]]
                    ;; 红色脏瓦片网格（屏幕坐标，直接绘制矩形）
                    (map (fn [tile-key]
                           (let [tx (TiledCanvas/unpackTx tile-key)
                                 ty (TiledCanvas/unpackTy tile-key)
                                 x1 (* tx tile-size)
                                 y1 (* ty tile-size)
                                 x2 (+ x1 tile-size)
                                 y2 (+ y1 tile-size)]
                             [:rect {:x x1
                                     :y y1
                                     :width (- x2 x1)
                                     :height (- y2 y1)
                                     :fill-color [1 0 0 0.2]
                                     :stroke-color [1 0 0 0.8]
                                     :stroke-width 0.5}]))
                         screen-dirties))))

              ;; 锚点
              (mapcat
                (fn [path-id]
                  (let [path (get paths path-id)
                        curve (:bezier-curve path)
                        points (:points curve)]
                    (map-indexed
                      (fn [idx point]
                        (let [world-pos (util/transform-point layer-transform
                                                              (:x point) (:y point))
                              screen-pos (vp/logic->screen viewport
                                                           (:x world-pos)
                                                           (:y world-pos))
                              is-selected (some #(and (= (:path-id %) path-id)
                                                      (= (:point-idx %) idx))
                                                selected-set)
                              is-active (and (= (:path-id active-anchor) path-id)
                                             (= (:point-idx active-anchor) idx))]
                          [:circle {:x (:x screen-pos)
                                    :y (:y screen-pos)
                                    :radius (if is-selected 8 5)
                                    :fill-color (cond
                                                  is-active (RGB/rgba 1 1 0 0.9)
                                                  is-selected (RGB/rgba 1 1 0 0.8)
                                                  :else (RGB/rgba 1 0 0 0.5))
                                    :stroke-color (cond is-active (RGB/rgba 1 1 0 1)
                                                        is-selected (RGB/rgba 0 0 1 1)
                                                        :else (RGB/rgba 1 0 0 1))
                                    :stroke-width (if is-active 2.0 1.5)}]))
                      points)))
                path-order)
              )

            ))
        ;; 图层不可见时候不显示 overlay
        []))))

(defn make-anchor-state
  [& {:keys [mode modal]
      :or {mode :translate
           modal false}}]
  (->AnchorState mode modal
                 nil #{} #{}
                 nil))


(rf/reg-event-fx
  store/app-id :anchor-translate/press
  [(cleanup-tool-interceptor AnchorState :factory (fn [_] (make-anchor-state)))
   (tool-context-interceptor)]
  (fn [cofx [_ _ _ _]]
    (let [ctx (:krro.painting/tool-context cofx)
          {:keys [ layer-event layer-type]} ctx]
      (if (= :vector layer-type)
        (let [record (:record cofx)]
          {:record (-> record
                       (assoc-in [:canvas-state :tool-data :last-layer-point] layer-event)) })
        {:fx [[:warn (str "Anchor tool is invalid for " layer-type)]]}))))

(rf/reg-event-fx
  store/app-id :anchor-translate/drag
  [(cleanup-tool-interceptor AnchorState :factory (fn [_] (make-anchor-state)))
   (tool-context-interceptor)
   (anchor-quadtree-interceptor)]
  (fn [cofx [_ record-id _ frame]]
    (let [ctx (:krro.painting/tool-context cofx)
          {:keys [layer-event layer layers layer-type layer-transform]} ctx]
      (if (= :vector layer-type)
        (let [record (:record cofx)
              tool-data (get-in record [:canvas-state :tool-data])
              selected (:selected-anchors tool-data #{})
              ]
          (when (seq selected)
            (when-let [last-layer-point (:last-layer-point tool-data)]
              (let [dx (- (:x layer-event) (:x last-layer-point))
                    dy (- (:y layer-event) (:y last-layer-point))
                    old-paths (:paths-map layer)
                    {:keys [paths aabb]}
                    (anchor/translate-anchors old-paths selected dx dy)
                    new-layer (assoc layer :paths-map paths)
                    new-layers (util/replace-layer new-layer layers)

                    ]
                (anchor-quadtree/update-anchor-quadtree! record-id old-paths paths selected)
                {:record (-> record
                             (assoc-in [:canvas-data :layers] new-layers)
                             (assoc-in [:canvas-state :tool-data :last-layer-point] layer-event))
                 :fx [[:tool/flush-overlay
                       (p/overlay tool-data ctx)
                       frame]
                      [:render-canvas record-id aabb layer-transform]]}))))
        {:fx [[:warn (str "Anchor tool is invalid for" layer-type)]]}))))

(rf/reg-event-fx
  store/app-id :anchor-translate/release
  [(cleanup-tool-interceptor AnchorState :factory (fn [_] (make-anchor-state)))
   (tool-context-interceptor)
   (anchor-quadtree-interceptor)]
  (fn [cofx [_ record-id event-map frame]]
    (profile
      {:id :anchor-translate/release}
      (p :anchor-translate/release
         (let [ctx (:krro.painting/tool-context cofx)
               {:keys [click-count layer-event layer-type layer-transform viewport]} ctx]
           (if (= :vector layer-type)
             (when (= 1 click-count)
               (let [record (:record cofx)
                     tool-data (get-in record [:canvas-state :tool-data])
                     selected-set (:selected-anchors tool-data #{})
                     shift? (get-in event-map [:modifiers :shift] false)
                     threshold 10.0
                     ^QuadTree  tree (get @anchor-quadtree/anchor-quadtrees record-id)
                     ^QuadTree$NearestResult result (.nearest tree (:x layer-event) (:y layer-event))]
                 (if (and result
                          (let [screen-pos (common/layer-point->screen
                                             {:x (.-x result)
                                              :y (.-y result)}
                                             layer-transform viewport)
                                dist (Math/hypot (- (:x screen-pos) (:x event-map))
                                                 (- (:y screen-pos) (:y event-map)))]
                            (< dist threshold)))
                   (let [closest-anchor (.-value result)
                         new-selected (if shift?
                                        (conj selected-set closest-anchor)
                                        #{closest-anchor})
                         new-tool-data (assoc tool-data :selected-anchors new-selected
                                                        :active-anchor closest-anchor)]
                     {:record (assoc-in record [:canvas-state :tool-data] new-tool-data)
                      :fx [[:tool/flush-overlay (p/overlay new-tool-data ctx) frame]]})
                   (if shift?
                     {:record record
                      :fx [[:tool/flush-overlay (p/overlay tool-data ctx) frame]]}
                     (let [new-tool-data (assoc tool-data :selected-anchors #{}
                                                          :active-anchor nil)]
                       {:record (assoc-in record [:canvas-state :tool-data] new-tool-data)
                        :fx [[:tool/flush-overlay (p/overlay new-tool-data ctx) frame]]})))))
             {:fx [[:warn (str "Anchor tool is invalid for" layer-type)]]}))))))

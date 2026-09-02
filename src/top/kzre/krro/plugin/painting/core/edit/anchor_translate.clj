(ns top.kzre.krro.plugin.painting.core.edit.anchor-translate
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
   [top.kzre.krro.plugin.painting.core.store :as store]
   [top.kzre.krro.plugin.painting.core.viewport :as vp])
  (:import
   (top.kzre.colorutils.color RGB)
   (top.kzre.krro.canvas.core QuadTree QuadTree$NearestResult)
   (top.kzre.krro.canvas.core.layer LayerUtils)))

(defrecord  AnchorState [selected-anchors ; selected 是一个 #{SelectedAnchor} 集合
                        layer-backup
                        init-layer-point]
  p/IToolData
  (cleanup! [_ ctx]
    (when-let [canvas-id (get-in ctx [:coeffects :record-id])]
      (swap! anchor-quadtree/anchor-quadtrees dissoc canvas-id)))

  (overlay [_ {:keys [viewport layer layer-type layer-visible layer-transform]}]
    (when (= :vector layer-type)
      (if layer-visible
        (let [paths (:paths-map layer)
              path-order (:path-order layer [])
              selected-set (or selected-anchors #{})]
          (when (seq path-order)
            (vec
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
                                                selected-set)]
                          [:circle {:x (:x screen-pos)
                                    :y (:y screen-pos)
                                    :radius (if is-selected 8 5)
                                    :fill-color (if is-selected
                                                  (RGB/rgba 1 1 0 0.8)
                                                  (RGB/rgba 1 0 0 0.5))
                                    :stroke-color (if is-selected
                                                    (RGB/rgba 1 1 0 1)
                                                    (RGB/rgba 1 0 0 1))
                                    :stroke-width 1.5}]))
                      points)))
                path-order))))
        ;; 图层不可见时候不显示 overlay
        []))))




(rf/reg-event-fx
  store/app-id :anchor-translate/press
  [(cleanup-tool-interceptor AnchorState :factory (fn [_] (->AnchorState #{} nil nil)))
   (tool-context-interceptor)]
  (fn [cofx [_ _ _ _]]
    (let [ctx (:krro.painting/tool-context cofx)
          {:keys [layer layer-event layer-type]} ctx]
      (if (= :vector layer-type)
        (let [record (:record cofx)
              init-pos (when (and layer-event (:x layer-event) (:y layer-event))
                         {:x (:x layer-event)
                          :y (:y layer-event)})]
          {:record (-> record
                       (assoc-in [:canvas-state :tool-data :init-layer-point] init-pos)
                       (assoc-in [:canvas-state :tool-data :layer-backup] layer)) })
        {:fx [[:warn (str "Anchor tool is invalid for " layer-type)]]}))))

(rf/reg-event-fx
  store/app-id :anchor-translate/drag
  [(cleanup-tool-interceptor AnchorState :factory (fn [_] (->AnchorState #{} nil nil)))
   (tool-context-interceptor)
   (anchor-quadtree-interceptor)]
  (fn [cofx [_ record-id _ frame]]
    (let [ctx (:krro.painting/tool-context cofx)
          {:keys [layer-event layer layers layer-type layer-transform]} ctx]
      (if (= :vector layer-type)
        (let [record (:record cofx)
              tool-data (get-in record [:canvas-state :tool-data])
              layer-backup (:layer-backup tool-data)
              selected (:selected-anchors tool-data #{})
              ]
          (when (and (seq selected) layer-backup)
            (when-let [init-layer-point (:init-layer-point tool-data)]
              (let [dx (- (:x layer-event) (:x init-layer-point))
                    dy (- (:y layer-event) (:y init-layer-point))
                    {:keys [paths aabb]}
                    (anchor/translate-anchors (:paths-map layer-backup) selected dx dy)
                    dirties (LayerUtils/aabbTiles pc/global-tile-size
                                                  (:min-x aabb)
                                                  (:min-y aabb)
                                                  (:max-x aabb)
                                                  (:max-y aabb))
                    new-layer (assoc layer-backup :paths-map paths)
                    new-layers (util/replace-layer new-layer layers)
                    old-paths (:paths-map layer)]
                (anchor-quadtree/update-anchor-quadtree! record-id old-paths paths selected)
                {:record (assoc-in record [:canvas-data :layers] new-layers)
                 :fx [[:tool/flush-overlay
                       (p/overlay tool-data ctx)
                       frame]
                      [:render-canvas record-id dirties layer-transform]]}))))
        {:fx [[:warn (str "Anchor tool is invalid for" layer-type)]]}))))

(rf/reg-event-fx
  store/app-id :anchor-translate/release
  [(cleanup-tool-interceptor AnchorState :factory (fn [_] (->AnchorState #{} nil nil)))
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
                         new-tool-data (assoc tool-data :selected-anchors new-selected)]
                     {:record (assoc-in record [:canvas-state :tool-data] new-tool-data)
                      :fx [[:tool/flush-overlay (p/overlay new-tool-data ctx) frame]]})
                   (if shift?
                     {:record record
                      :fx [[:tool/flush-overlay (p/overlay tool-data ctx) frame]]}
                     (let [new-tool-data (assoc tool-data :selected-anchors #{})]
                       {:record (assoc-in record [:canvas-state :tool-data] new-tool-data)
                        :fx [[:tool/flush-overlay (p/overlay new-tool-data ctx) frame]]})))))
             {:fx [[:warn (str "Anchor tool is invalid for" layer-type)]]}))))))

(ns top.kzre.krro.plugin.painting.core.edit.anchor
  (:require
   [top.kzre.krro.canvas.core.layer.util :as util]
   [top.kzre.krro.core.reframe :as rf]
   [top.kzre.krro.curve.bezier2d.core :as bezier]
   [top.kzre.krro.plugin.painting.core.edit.common :as common]
   [top.kzre.krro.plugin.painting.core.edit.interceptors :refer [cleanup-tool-interceptor
                                                                 tool-context-interceptor]]
   [top.kzre.krro.plugin.painting.core.edit.protocol :as p]
   [top.kzre.krro.plugin.painting.core.store :as store]
   [top.kzre.krro.plugin.painting.core.viewport :as vp]
   [top.kzre.krro.plugin.painting.core.project.canvas :as pc])
  (:import
    (top.kzre.colorutils.color RGB)
    (top.kzre.krro.canvas.core.layer LayerUtils)))

(defrecord SelectedAnchor [path-id point-idx])

(defrecord AnchorState [selected layer-backup init-layer-point]   ; selected 是一个 #{SelectedAnchor} 集合
  p/IToolData
  (cleanup! [_] nil)

  (overlay [_ {:keys [viewport layer layer-transform]}]
    (when (= :vector (:type layer))
      (let [paths (:paths-map layer)
            path-order (:path-order layer [])
            selected-set (or selected #{})]
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
              path-order)))))))


(rf/reg-event-fx
  store/app-id :anchor-tool/press
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
  store/app-id :anchor-tool/drag
  [(cleanup-tool-interceptor AnchorState :factory (fn [_] (->AnchorState #{} nil nil)))
   (tool-context-interceptor)]
  (fn [cofx [_ record-id _ frame]]
    (let [ctx (:krro.painting/tool-context cofx)
          {:keys [layer-event layers layer-type layer-transform]} ctx]
      (if (= :vector layer-type)
        (let [record (:record cofx)
              tool-data (get-in record [:canvas-state :tool-data])
              layer-backup (:layer-backup tool-data)
              selected (:selected tool-data #{})
              selected-groups (group-by :path-id selected)
              ]
          (when (and (seq selected-groups) layer-backup)
            (when-let [init-layer-point (:init-layer-point tool-data)]
              (let [dx (- (:x layer-event) (:x init-layer-point))
                    dy (- (:y layer-event) (:y init-layer-point))
                    [new-paths aabb]
                    (reduce
                      (fn [[paths aabb] g]
                        (let [path-id (key g)]
                          (if-let [path (get paths path-id)]
                            (let [idxs (mapv #(:point-idx %) (val g))
                                  ;; TODO catmull-rom 分支
                                  old-curve (:bezier-curve path)
                                  new-curve (apply bezier/translate old-curve dx dy idxs)
                                  old-aabb (apply bezier/aabb old-curve idxs)
                                  new-aabb (apply bezier/aabb new-curve idxs)
                                  all-aabb (bezier/merge-aabb old-aabb new-aabb aabb)]
                              [(assoc paths path-id (assoc path :bezier-curve new-curve))
                               all-aabb])
                            [paths aabb])))
                      [(:paths-map layer-backup) nil]
                      selected-groups)
                    dirties (LayerUtils/aabbTiles pc/global-tile-size
                                                  (:min-x aabb)
                                                  (:min-y aabb)
                                                  (:max-x aabb)
                                                  (:max-y aabb))
                    new-layer (assoc layer-backup :paths-map new-paths)
                    new-layers (util/replace-layer new-layer layers)]
                {:record (assoc-in record [:canvas-data :layers] new-layers)
                 :fx [[:tool/flush-overlay
                       (p/overlay tool-data ctx)
                       frame]
                      [:render-canvas record-id dirties layer-transform]]}))
            ))
        {:fx [[:warn (str "Anchor tool is invalid for" layer-type)]]}))))

(rf/reg-event-fx
  store/app-id :anchor-tool/release
  [(cleanup-tool-interceptor AnchorState :factory (fn [_] (->AnchorState #{} nil nil)))
   (tool-context-interceptor)]
  (fn [cofx [_ _ event-map frame]]
    (let [ctx (:krro.painting/tool-context cofx)
          {:keys [click-count layer layer-type layer-transform viewport ]} ctx]
      (if (= :vector layer-type)
        (when (= 1 click-count)
          (let [record (:record cofx)
                tool-data (get-in record [:canvas-state :tool-data])
                selected-set (:selected tool-data #{})
                shift? (get-in event-map [:modifiers :shift] false)
                paths (:paths-map layer)
                path-order (:path-order layer [])
                threshold 10.0   ; 像素阈值
                closest (atom nil)
                closest-dist (atom Double/MAX_VALUE)]
            (doseq [path-id path-order
                    :let [path (get paths path-id)
                          curve (:bezier-curve path)
                          points (:points curve)]]
              (doseq [idx (range (count points))
                      :let [point (nth points idx)
                            screen-pos (common/layer-point->screen point layer-transform viewport)
                            dist (Math/hypot (- (:x screen-pos) (:x event-map))
                                             (- (:y screen-pos) (:y event-map)))]]
                (when (< dist @closest-dist)
                  (reset! closest-dist dist)
                  (reset! closest {:path-id path-id :point-idx idx}))))
            (if (and @closest (< @closest-dist threshold))
              (let [new-selected (if shift?
                                   (conj selected-set (map->SelectedAnchor @closest))
                                   #{(->SelectedAnchor (-> @closest :path-id) (-> @closest :point-idx))})
                    new-tool-data (assoc tool-data :selected new-selected)]
                {:record (assoc-in record [:canvas-state :tool-data] new-tool-data)
                 :fx [[:tool/flush-overlay
                       (p/overlay new-tool-data ctx)
                       frame]]})
              (if shift?
                {:record record}   ; 未选中任何点，不改变选择
                (let [new-tool-data (assoc tool-data :selected #{})]
                  {:record (assoc-in record [:canvas-state :tool-data ] new-tool-data)
                   :fx [[:tool/flush-overlay
                         (p/overlay new-tool-data ctx)
                         frame]]})))))
        {:fx [[:warn (str "Anchor tool is invalid for" layer-type)]]}))))
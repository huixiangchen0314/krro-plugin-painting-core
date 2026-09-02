(ns top.kzre.krro.plugin.painting.core.edit.anchor
  (:require
   [taoensso.tufte :refer [profile p]]
   [top.kzre.krro.canvas.core.layer.util :as util]
   [top.kzre.krro.core.reframe :as rf]
   [top.kzre.krro.plugin.painting.core.algo.anchor :as anchor]
   [top.kzre.krro.plugin.painting.core.algo.anchor-quadtree :as anchor-quadtree]
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

;; 锚点四叉树加速结构
(defonce ^:private anchor-quadtrees (atom {}))


(defrecord AnchorState [selected-anchors layer-backup init-layer-point]   ; selected 是一个 #{SelectedAnchor} 集合
  p/IToolData
  (cleanup! [_ ctx]
    (when-let [canvas-id (get-in ctx [:coeffects :record-id])]
      (swap! anchor-quadtrees dissoc canvas-id)))

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


(defn anchor-quadtree-interceptor
  "更新前确保 anchor-quadtree存在"
  []
  {:before
   (fn [context]
     ;; 确保派生数据
     (when-let [canvas-id (get-in context [:coeffects :record-id ])]
       (when-not (get @anchor-quadtrees canvas-id)
         (let [canvas-data (get-in context [:coeffects :record :canvas-data])]
           (when-let [current-layer-id (:current-layer-id canvas-data)]
             (let [layers (:layers canvas-data)]
               (when-let [layer (util/find-layer current-layer-id layers)]
                 (when (= :vector (:type layer))
                   (let [paths (:paths-map layer)
                         tree (anchor-quadtree/build-anchor-quadtree paths)]
                     (swap! anchor-quadtrees assoc canvas-id tree)))))))))
     context)})

(defn update-anchor-quadtree!
  "增量更新画布的锚点四叉树：删除旧点，插入新点。
   old-paths 和 new-paths 是路径映射，anchors 是修改的锚点集合。"
  [canvas-id old-paths new-paths anchors]
  (let [^QuadTree tree (get @anchor-quadtrees canvas-id)]
    (if tree
      (anchor-quadtree/update-anchor-quadtree tree old-paths new-paths anchors)
      ;; 树不存在，完全重建
      (let [new-tree (anchor-quadtree/build-anchor-quadtree new-paths)]
        (swap! anchor-quadtrees assoc canvas-id new-tree)))))

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
   (tool-context-interceptor)
   (anchor-quadtree-interceptor)]
  (fn [cofx [_ record-id _ frame]]
    (let [ctx (:krro.painting/tool-context cofx)
          {:keys [layer-event layers layer-type layer-transform]} ctx]
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
                    new-layers (util/replace-layer new-layer layers)]
                (update-anchor-quadtree! record-id (:paths-map layer-backup) paths selected)
                {:record (assoc-in record [:canvas-data :layers] new-layers)
                 :fx [[:tool/flush-overlay
                       (p/overlay tool-data ctx)
                       frame]
                      [:render-canvas record-id dirties layer-transform]]}))))
        {:fx [[:warn (str "Anchor tool is invalid for" layer-type)]]}))))

(rf/reg-event-fx
  store/app-id :anchor-tool/release
  [(cleanup-tool-interceptor AnchorState :factory (fn [_] (->AnchorState #{} nil nil)))
   (tool-context-interceptor)
   (anchor-quadtree-interceptor)]
  (fn [cofx [_ record-id event-map frame]]
    (profile
      {:id :anchor-tool/release}
      (p :anchor-tool/release
         (let [ctx (:krro.painting/tool-context cofx)
               {:keys [click-count layer-event layer-type layer-transform viewport ]} ctx]
           (if (= :vector layer-type)
             (when (= 1 click-count)
               (let [record (:record cofx)
                     tool-data (get-in record [:canvas-state :tool-data])
                     selected-set (:selected-anchors tool-data #{})
                     shift? (get-in event-map [:modifiers :shift] false)
                     threshold 10.0
                     tree (get @anchor-quadtrees record-id)
                     ^QuadTree$NearestResult result (.nearest ^QuadTree tree (:x layer-event) (:y layer-event))]
                 (if (and (some? result)
                          (let [screen-pos (common/layer-point->screen
                                             {:x (.-x result)
                                              :y (.-y result)}
                                             layer-transform viewport)
                                dist (Math/hypot (- (:x screen-pos) (:x event-map))
                                                 (- (:y screen-pos) (:y event-map)))]
                            (< dist threshold)))
                   (let [cloest-anchor (.-value result)
                         new-selected (if shift?
                                        (conj selected-set cloest-anchor)
                                        #{cloest-anchor})
                         new-tool-data (assoc tool-data :selected-anchors new-selected)]
                     {:record (assoc-in record [:canvas-state :tool-data] new-tool-data)
                      :fx [[:tool/flush-overlay
                            (p/overlay new-tool-data ctx)
                            frame]]})
                   (if shift?
                     {:record record}   ; 未选中任何点，不改变选择
                     (let [new-tool-data (assoc tool-data :selected-anchors #{})]
                       {:record (assoc-in record [:canvas-state :tool-data ] new-tool-data)
                        :fx [[:tool/flush-overlay
                              (p/overlay new-tool-data ctx)
                              frame]]})))))
             {:fx [[:warn (str "Anchor tool is invalid for" layer-type)]]})))       )))
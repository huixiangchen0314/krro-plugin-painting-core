(ns top.kzre.krro.plugin.painting.core.edit.anchor.anchor
  (:require
    [taoensso.tufte :refer [p profile]]
    [top.kzre.krro.canvas.core.layer.util :as util]
    [top.kzre.krro.canvas.vector.core :as cv]
    [top.kzre.krro.core.reframe.core :as rf]
    [top.kzre.krro.plugin.painting.core.algo.segment]
    [top.kzre.krro.plugin.painting.core.edit.anchor.anchor-quadtree :as tree :refer [anchor-quadtree-interceptor]]
    [top.kzre.krro.plugin.painting.core.edit.anchor.state :as anchor.state]
    [top.kzre.krro.plugin.painting.core.edit.common :as common]
    [top.kzre.krro.plugin.painting.core.edit.interceptors :refer [cleanup-tool-interceptor
                                                                  tool-context-interceptor]]
    [top.kzre.krro.plugin.painting.core.edit.protocol :as p]
    [top.kzre.krro.plugin.painting.core.store :as store])
  (:import
    (top.kzre.krro.canvas.core QuadTree QuadTree$NearestResult)
    (top.kzre.krro.plugin.painting.core.edit.anchor.state AnchorState)))

(rf/reg-event-fx
  store/app-id :anchor-translate/press
  [(cleanup-tool-interceptor AnchorState :factory (fn [_] (anchor.state/make-anchor-state)))
   (tool-context-interceptor)]
  (fn [cofx [_ _ _ _]]
    (let [ctx (:krro.painting/tool-context cofx)
          {:keys [ layer-event layer-type]} ctx]
      (if (= :vector layer-type)
        (let [record (:record cofx)]
          {:record (-> record
                       (assoc-in [:canvas-state :tool-data :last-layer-point] layer-event)) })
        {:fx [[:warn (str "Anchor tool is invalid for " layer-type)]]}))))

;; drag 是立即应用
(rf/reg-event-fx
  store/app-id :anchor-translate/drag
  [(cleanup-tool-interceptor AnchorState :factory (fn [_] (anchor.state/make-anchor-state)))
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
                    old-paths (:paths layer)
                    paths
                    (cv/translate-anchors old-paths selected dx dy)
                    new-layer (assoc layer :paths paths)
                    new-layers (util/replace-layer new-layer layers)
                    ]
                (tree/update-anchors! record-id old-paths paths selected)
                {:record (-> record
                             (assoc-in [:canvas-data :layers] new-layers)
                             (assoc-in [:canvas-state :tool-data :last-layer-point] layer-event))
                 :fx [[:tool/flush-overlay
                       (p/overlay tool-data ctx)
                       frame]
                      [:render-canvas record-id nil layer-transform]]}))))
        {:fx [[:warn (str "Anchor tool is invalid for" layer-type)]]}))))

(rf/reg-event-fx
  store/app-id :anchor-translate/release
  [(cleanup-tool-interceptor AnchorState :factory (fn [_] (anchor.state/make-anchor-state)))
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
                     ^QuadTree  tree (tree/tree record-id)
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


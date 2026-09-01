(ns top.kzre.krro.plugin.painting.core.edit.vector-brush
  (:require [top.kzre.krro.brush.vector :as vec-brush]
            [top.kzre.krro.canvas.core.layer.util :as util]
            [top.kzre.krro.core.reframe :as rf]
            [top.kzre.krro.curve.bezier2d.core :as bezier]
            [top.kzre.krro.plugin.painting.core.edit.interceptors :refer [cleanup-tool-interceptor]]
            [top.kzre.krro.plugin.painting.core.edit.protocol :as p]
            [top.kzre.krro.plugin.painting.core.layer.clone :as clone]
            [top.kzre.krro.plugin.painting.core.store :as store]
            [top.kzre.krro.plugin.painting.core.tool.stroke :as stroke]
            [top.kzre.krro.plugin.painting.core.tool.util :as tool-util]
            [top.kzre.krro.plugin.painting.core.viewport :as vp])
  (:import (top.kzre.colorutils.color RGB)
           (top.kzre.krro.util.math KMath)))

(defn- add-path-to-layer
  [backup-layer path-data path-id]
  (let [curve-edn (bezier/curve->edn (:curve path-data))
        new-path {:path-type :bezier
                  :bezier-curve curve-edn
                  :style {:stroke {:color (RGB/rgba 0.6 0 0 1)
                                   :width 50
                                   :cap   :round
                                   :join  :square}}
                  :width-samples (:width-samples path-data)
                  :arc-params (:arc-params path-data)}]
    (-> backup-layer
        (assoc-in [:paths-map path-id] new-path)
        (update :path-order conj path-id))))

(defrecord VectorBrushState [stroke layer-backup layer-transform layer-transform-inv]
  p/IToolData
  (cleanup! [_] nil)
  (overlay [_ _] nil))

(rf/reg-event-fx
  store/app-id :vector-brush-tool/press
  [(cleanup-tool-interceptor)]
  (fn [cofx [_ _ _ _]]
    (let [record (:record cofx)]
      (if-let [current-layer-id (get-in record [:canvas-data :current-layer-id])]
        (let [layers (get-in record [:canvas-data :layers])
              layer-path (util/find-layer-path current-layer-id layers)
              layer (util/find-layer-by-path layer-path layers)]
          (if (= :vector (:type layer))
            (let [layer-transform-inv (tool-util/layer-transform-inverse layer layers)
                  layer-transform (KMath/mat2dInv layer-transform-inv)]
              {:record
               (assoc-in record [:canvas-state :tool-data]
                         (->VectorBrushState (stroke/make-stroke) (clone/clone-layer layer)
                                             layer-transform layer-transform-inv))
               :fx
               [[:tool/set-command-enabled false]]})
            {:fx [[:warn "Vector brush tool is only used for vector layer!"]]}))
        {:fx [[:warn "No active layer!"]]}))))

(rf/reg-event-fx
  store/app-id :vector-brush-tool/drag
  (fn [cofx [_ record-id event-map frame]]
    (let [record (:record cofx)
          tool-data (get-in record [:canvas-state :tool-data])
          ]
      (when (instance? VectorBrushState tool-data)
        (let [{:keys [stroke layer-transform-inv layer-backup]} tool-data
              logic-pos (vp/screen->logic (vp/get-viewport frame)
                                          (:x event-map) (:y event-map))
              local-pos (util/transform-point layer-transform-inv
                                              (:x logic-pos) (:y logic-pos))
              local-event (assoc event-map :x (:x local-pos) :y (:y local-pos))
              pevent (stroke/->pointer-event local-event)
              new-stroke (.append stroke pevent)
              stroke-v (.getStroke new-stroke)]
          (if-let [result (vec-brush/render-vector-stroke stroke-v)]
            (let [preview-id (keyword (str "preview-" (System/currentTimeMillis)))
                  new-layer (add-path-to-layer layer-backup result preview-id)
                  layers (get-in record [:canvas-data :layers])
                  new-layers (util/replace-layer new-layer layers)]
              {:record (-> record
                           (assoc-in [:canvas-state :tool-data :stroke] new-stroke)
                           (assoc-in [:canvas-data :layers] new-layers))
               :fx [[:render-canvas record-id nil nil]]})
            {:record (assoc-in record [:canvas-state :tool-data :stroke] new-stroke)
             :fx [[:render-canvas record-id nil nil]]}))))))


(rf/reg-event-fx
  store/app-id :vector-brush-tool/release
  (fn [cofx [_ record-id event-map frame]]
    (let [record (:record cofx)
          tool-data (get-in record [:canvas-state :tool-data])
          ]
      (when (instance? VectorBrushState tool-data)
        (let [{:keys [stroke layer-transform-inv layer-backup]} tool-data
              logic-pos (vp/screen->logic (vp/get-viewport frame)
                                          (:x event-map) (:y event-map))
              local-pos (util/transform-point layer-transform-inv
                                              (:x logic-pos) (:y logic-pos))
              local-event (assoc event-map :x (:x local-pos) :y (:y local-pos))
              pevent (stroke/->pointer-event local-event)
              new-stroke (.append stroke pevent)
              stroke-v (.getStroke new-stroke)]
          (if-let [result (vec-brush/render-vector-stroke stroke-v)]
            (let [preview-id (keyword (str "preview-" (System/currentTimeMillis)))
                  new-layer (add-path-to-layer layer-backup result preview-id)
                  layers (get-in record [:canvas-data :layers])
                  new-layers (util/replace-layer new-layer layers)]
              {:record (-> record
                           (assoc-in [:canvas-data :layers] new-layers))
               :fx [[:render-canvas record-id nil nil]]})
            {:fx [[:render-canvas record-id nil nil]]}))))))
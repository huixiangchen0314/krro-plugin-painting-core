(ns top.kzre.krro.plugin.painting.core.edit.brush
  (:require
   [top.kzre.krro.brush.core :as brush-core]
   [top.kzre.krro.canvas.core.layer.util :as util]
   [top.kzre.krro.core.reframe :as rf]
   [top.kzre.krro.plugin.painting.core.edit.common :as common]
   [top.kzre.krro.plugin.painting.core.edit.interceptors :refer [cleanup-tool-interceptor]]
   [top.kzre.krro.plugin.painting.core.edit.protocol :as p]
   [top.kzre.krro.plugin.painting.core.layer.clone :as clone]
   [top.kzre.krro.plugin.painting.core.store :as store]
   [top.kzre.krro.plugin.painting.core.tool.stroke :as stroke]
   [top.kzre.krro.plugin.painting.core.viewport :as vp])
  (:import
   (top.kzre.krro.brush Stroke)
   (top.kzre.krro.canvas.core.layer LayerUtils)
   [top.kzre.krro.util.math KMath]
   (top.kzre.krro.util.tile TiledCanvas)))

(defrecord BrushState [stroke layer-backup rendered-event-count]
 p/IToolData
 (cleanup [_]
   (when-let [^TiledCanvas canvas  (:canvas layer-backup)]
     (.clear canvas)))
  (overlay [_] nil))

(rf/reg-event-fx
  store/app-id :brush-tool/press
  [(cleanup-tool-interceptor)]
  (fn [cofx [_ _ _ _]]
    (let [record (:record cofx)]
      (if-let [current-layer-id (get-in record [:canvas-data :current-layer-id])]
        (let [layers (get-in record [:canvas-data :layers])
              layer (util/find-layer current-layer-id layers)]
          (if (= :raster (:type layer))
            {:record
             (-> record
                 (assoc-in [:canvas-state :tool-data]
                           ;; TODO 笔刷管理
                           (->BrushState (stroke/make-stroke) (clone/clone-layer) 0)))
             :fx
             [:set-command-enabled false]}
            {:fx [[:warn "Brush tool is only used for raster layer!"]]}))
        {:fx [[:warn "No active layer!"]]}))))

(rf/reg-event-fx
  store/app-id :brush-tool/drag
  (fn [cofx [_ record-id event-map frame]]
    (let [record (:record cofx)
          tool-data (get-in record [:canvas-state :tool-data])]
      (when (instance? BrushState tool-data)
        (let [
              ;; 准备好画布
              layer-id (get-in record [:canvas-data :current-layer-id])
              layers (get-in record [:canvas-data :layers])
              layer (util/find-layer layer-id layers)
              ^TiledCanvas layer-canvas (:canvas layer)
              tile-size (.getTileSize layer-canvas)

              ;; 准备笔触
              layer-transform (get-in record [:canvas-state :layer-transform])
              ^Stroke stroke (get-in record [:canvas-state :tool-data :stroke])
              layer-transform-inv (get-in record [:canvas-state :layer-transform-inv] )
              event-map'
              (merge
                ;; 最终变换 = 图层变换x视口变换
                (util/transform-point (KMath/mat2dMul layer-transform-inv
                                                      (vp/get-viewport-transform frame))
                                      (:x event-map) (:y event-map))
                event-map)
              pevent (stroke/->pointer-event event-map')
              new-stroke (.append stroke pevent)
              ]
          (when (> (.size new-stroke) (.size stroke))
            (let [last-count (- (.size new-stroke)
                                (get-in record [:canvas-state :tool-data :rendered-event-count]))
                  stroke' (.last new-stroke last-count)
                  [new-canvas dirties] (brush-core/render-stroke!
                                         layer-canvas
                                         stroke')
                  new-layers (util/replace-layer (assoc layer :canvas new-canvas)
                                                 layers)
                  world-dirties (set (LayerUtils/transformTiles dirties tile-size layer-transform))]
              {:record (-> record
                           (assoc-in [:canvas-data :layers] new-layers)
                           (assoc-in [:canvas-state :tool-data :stroke] new-stroke)
                           (update-in [:canvas-state :tool-data :rendered-event-count] (fn [c] (+ c last-count))))
               :fx [[:render-canvas record-id world-dirties]]})))))))

(rf/reg-event-fx
  store/app-id :brush-tool/release
  (fn [cofx [_ record-id]]
    (let [record (:record cofx)
          tool-data (get-in record [:canvas-state :tool-data])]
      (when  (instance? BrushState tool-data)
        (let [stroke (:stroke tool-data)]
          (if (and stroke (> (.size stroke) 0))
            (let [layer-id (get-in record [:canvas-data :current-layer-id])
                  layers (get-in record [:canvas-data :layers])
                  layer (util/find-layer layer-id layers)
                  layer-canvas (:canvas layer)
                  tile-size (.getTileSize layer-canvas)
                  layer-transform (get-in record [:canvas-state :layer-transform])
                  backup-layer (:layer-backup tool-data)
                  backup-canvas (:canvas backup-layer)
                  ;; 旧画布备份，注意，所有权转移给了 undo 控制
                  ^TiledCanvas old-canvas
                  (doto (TiledCanvas. (.getTileSize backup-canvas)
                                      (.getDefaultPixel backup-canvas))
                    (.shareFrom backup-canvas))
                  [new-canvas dirties] (brush-core/render-stroke! backup-canvas stroke)
                  updated-canvas (.mergeCanvas layer-canvas new-canvas)
                  new-layer (assoc layer :canvas updated-canvas)
                  new-layers (util/replace-layer new-layer layers)
                  world-dirties (set (LayerUtils/transformTiles dirties tile-size layer-transform))
                  ]
              {:record (-> record
                           (assoc-in [:canvas-data :layers] new-layers)
                           (common/cleanup-tool-data!))
               :fx [[:record-raster-layer-edited record-id layer-id old-canvas new-canvas world-dirties]
                    [:render-request record-id world-dirties]
                    [:set-command-enabled true]]})
            ;; else
            {:record (common/cleanup-tool-data! record)
             :fx [[:set-command-enabled true]]}))))))
(ns top.kzre.krro.plugin.painting.core.edit.brush
  (:require
   [top.kzre.krro.brush.core :as brush-core]
   [top.kzre.krro.canvas.core.layer.util :as util]
   [top.kzre.krro.core.reframe :as rf]
   [top.kzre.krro.plugin.painting.core.edit.common :as common]
   [top.kzre.krro.plugin.painting.core.layer.clone :as clone]
   [top.kzre.krro.plugin.painting.core.layer.dispose :as dispose]
   [top.kzre.krro.plugin.painting.core.store :as store]
   [top.kzre.krro.plugin.painting.core.tool.stroke :as stroke])
  (:import
    (top.kzre.krro.brush Stroke)
    [top.kzre.krro.canvas.core.layer LayerUtils]
    (top.kzre.krro.util.tile TiledCanvas)))

(rf/reg-event-fx
  store/app-id :brush-tool/press
  (fn [cofx [_ record-id event-map frame]]
    (let [record (:record cofx)]
      (if-let [current-layer-id (get-in record [:canvas-data :current-layer-id])]
        (let [layers (get-in record [:canvas-data :layers])
              layer (util/find-layer current-layer-id layers)]
          (if (= :raster (:type layer))
            {:record
             (-> record
                 (assoc-in [:canvas-state :tool-data]
                           ;; TODO 笔刷管理
                           {:brush/stroke (stroke/make-stroke)
                            :brush/layer-backup (clone/clone-layer)
                            :brush/active? true
                            }))
             :fx
             [:disable-command]}
            {:fx [[:warn "Brush tool is only used for raster layer!"]]}))
        {:fx [[:warn "No active layer!"]]}))))

(rf/reg-fx
  store/app-id :brush-tool/drag
  (fn [cofx [_ record-id event-map _]]
    (let [record (:record cofx)
          tool-data (get-in record [:canvas-state :tool-data])
          active? (:brush/active? tool-data)]
      (when active?
        (let [
              ;; 准备好画布
              layer-id (get-in record [:canvas-data :current-layer-id])
              layers (get-in record [:canvas-data :layers])
              layer (util/find-layer layer-id layers)
              ^TiledCanvas layer-canvas (:canvas layer)
              tile-size (.getTileSize layer-canvas)

              layer-transform (get-in record [:canvas-state :layer-transform])
              ^Stroke stroke (get-in record [:canvas-state :tool-data :brush/stroke])
              layer-transform-inv (get-in record [:canvas-state :layer-transform-inv] )
              event-map'
              (merge
                (util/transform-point layer-transform-inv (:x event-map) (:y event-map))
                event-map)
              pevent (stroke/->pointer-event event-map')
              new-stroke (.append stroke pevent)

              stroke' (.last new-stroke)
              [new-canvas dirties] (brush-core/render-stroke!
                                     layer-canvas
                                     stroke')
              new-layers (util/replace-layer (assoc layer :canvas new-canvas)
                                             layers)
              world-dirties (set (LayerUtils/transformTiles dirties tile-size layer-transform))]
          {:record (-> record
                       (assoc-in [:canvas-data :layers] new-layers)
                        (assoc-in [:canvas-state :tool-data :brush/stroke] new-stroke))
           :fx [[:render-canvas record-id world-dirties]]})))))

(rf/reg-fx
  store/app-id :brush-tool/release
  (fn [cofx [_ record-id]]
    (let [record (:record cofx)
          tool-data (get-in record [:canvas-state :tool-data])
          active? (:brush/active? tool-data)]
      (when active?
        (dispose/dispose-layer (:brush/layer-backup tool-data))
        (let [current-layer-id (get-in record [:canvas-data :current-layer-id])]
          {:record (-> record
                       (common/clear-tool-data))
           :fx [[:record-raster-layer-edited record-id current-layer-id]
                [:enable-command]]})))))
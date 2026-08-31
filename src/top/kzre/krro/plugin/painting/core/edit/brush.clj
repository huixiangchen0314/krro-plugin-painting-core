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
    [top.kzre.krro.plugin.painting.core.tool.util :as tool-util]
    [top.kzre.krro.plugin.painting.core.viewport :as vp])
  (:import
    (top.kzre.krro.util.math KMath)
   (top.kzre.krro.util.tile TiledCanvas)))

(defrecord BrushState [stroke layer-backup
                       ^int rendered-event-count
                       layer-transform
                       layer-transform-inv]
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
              layer-path (util/find-layer-path current-layer-id layers)
              layer (util/find-layer-by-path layer-path layers)]
          (if (= :raster (:type layer))
            (let [layer-transform-inv (tool-util/layer-transform-inverse layer layers)
                  layer-transform (KMath/mat2dInv layer-transform-inv)]
              {:record
               (assoc-in record [:canvas-state :tool-data]
                         ;; TODO 笔刷管理
                         (->BrushState (stroke/make-stroke) (clone/clone-layer layer) 0
                                       layer-transform layer-transform-inv))
               :fx
               [[:tool/set-command-enabled false]]})
            {:fx [[:warn "Brush tool is only used for raster layer!"]]}))
        {:fx [[:warn "No active layer!"]]}))))

(rf/reg-event-fx
  store/app-id :brush-tool/drag
  (fn [cofx [_ record-id event-map frame]]
    (let [record (:record cofx)
          tool-data (get-in record [:canvas-state :tool-data])]
      (when (instance? BrushState tool-data)
        (let [{:keys [stroke layer-transform layer-transform-inv]} tool-data
              ;; 准备好画布
              layer-id (get-in record [:canvas-data :current-layer-id])
              layers (get-in record [:canvas-data :layers])
              layer (util/find-layer layer-id layers)
              ^TiledCanvas layer-canvas (:canvas layer)
              p1 (vp/screen->logic (vp/get-viewport frame) (:x event-map) (:y event-map))
              p2 (util/transform-point layer-transform-inv (:x p1) (:y p1))
              event-map' (merge event-map p2)
              pevent (stroke/->pointer-event event-map')
              new-stroke (.append stroke pevent)
              ]
          (when (> (.size new-stroke) (.size stroke))
            (let [last-count (- (.size new-stroke)
                                (get-in record [:canvas-state :tool-data :rendered-event-count]))
                  stroke' (.last new-stroke last-count)
                  [new-canvas dirties] (brush-core/render-stroke
                                         layer-canvas
                                         stroke')
                  dirties-set (set dirties)
                  new-layers (util/replace-layer (assoc layer :canvas new-canvas)
                                                 layers)
                  ]
              {:record (-> record
                           (assoc-in [:canvas-data :layers] new-layers)
                           (assoc-in [:canvas-state :tool-data :stroke] new-stroke)
                           (update-in [:canvas-state :tool-data :rendered-event-count] (fn [c] (+ c last-count))))
               :fx [[:render-canvas record-id dirties-set layer-transform]]})))))))

(rf/reg-event-fx
  store/app-id :brush-tool/release
  (fn [cofx [_ record-id]]
    (let [record (:record cofx)
          tool-data (get-in record [:canvas-state :tool-data])]
      (when  (instance? BrushState tool-data)
        (let [{:keys [stroke layer-transform layer-backup]} tool-data]
          (if (and stroke (> (.size stroke) 0))
            (let [layer-id (get-in record [:canvas-data :current-layer-id])
                  layers (get-in record [:canvas-data :layers])
                  layer (util/find-layer layer-id layers)
                  layer-canvas (:canvas layer)
                  backup-canvas (:canvas layer-backup)
                  ;; 旧画布备份，注意，所有权转移给了 undo 控制
                  ^TiledCanvas old-canvas (.copy backup-canvas)
                  [new-canvas dirties] (brush-core/render-stroke backup-canvas stroke)
                  dirties-set (set dirties)
                  updated-canvas (.mergeCanvas layer-canvas new-canvas)
                  new-layer (assoc layer :canvas updated-canvas)
                  new-layers (util/replace-layer new-layer layers)
                  ]
              {:record (-> record
                           (assoc-in [:canvas-data :layers] new-layers)
                           (common/cleanup-tool-data!))
               :fx [[:record-raster-layer-edited record-id layer-id old-canvas new-canvas dirties-set]
                    [:render-canvas record-id dirties-set layer-transform]
                    [:tool/set-command-enabled true]]})
            ;; else
            {:record (common/cleanup-tool-data! record)
             :fx [[:tool/set-command-enabled true]]}))))))
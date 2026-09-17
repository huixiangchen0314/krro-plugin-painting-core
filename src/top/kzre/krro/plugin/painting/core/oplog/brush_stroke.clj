(ns top.kzre.krro.plugin.painting.core.oplog.brush-stroke
  (:require
   [top.kzre.krro.brush.core :as brush-core]
   [top.kzre.krro.canvas.core.layer.util :as util]
   [top.kzre.krro.plugin.painting.core.change.raster-layer :as change]
   [top.kzre.krro.plugin.painting.core.oplog.oplog :as oplog]
   [top.kzre.krro.plugin.painting.core.record :as record]
   [top.kzre.krro.plugin.painting.core.oplog.raster-layer :as raster-layer])
  (:import
   (top.kzre.krro.util.tile TiledCanvas)))

(defonce ^:private preview-brush-stroke-action-key* :preview-brush-stroke)
(defn preview-brush-stroke-action-key []
  preview-brush-stroke-action-key*)

(defn make-preview-brush-stroke
  [layer-id stroke]
  {:type (preview-brush-stroke-action-key)
   :transient true
   :layer-id layer-id
   :stroke stroke})

(defmethod oplog/apply-action! (preview-brush-stroke-action-key)
  [{:keys [layer-id stroke]} record]
  (let [{:keys [layer layers layer-transform]}
        (record/layer-context record layer-id)
        ^TiledCanvas layer-canvas (:canvas layer)
        [rendered-canvas dirties] (brush-core/render-stroke layer-canvas stroke)
        new-layer (assoc layer :canvas rendered-canvas)
        new-layers (util/replace-layer new-layer layers)]
    (.close layer-canvas)
    [(assoc-in record [:canvas-data :layers] new-layers)
     (change/make-raster-layer-dirty layer-id (.copy rendered-canvas) dirties layer-transform)]))


(defonce ^:private brush-stroke-action-key* :brush-stroke)
(defn brush-stroke-action-key []
  brush-stroke-action-key*)

(defn make-brush-stroke
  [layer-id stroke layer-backup]
  {:type (brush-stroke-action-key)
   :transient false
   :layer-id layer-id
   :stroke stroke
   :layer-backup layer-backup})

(defmethod oplog/apply-action! (brush-stroke-action-key)
  [{:keys [layer-id stroke layer-backup]} record]
  (let [{:keys [layer layers layer-transform]}
        (record/layer-context record layer-id)
        ^TiledCanvas backup-canvas (:canvas layer-backup)
        ^TiledCanvas layer-canvas (:canvas layer)
        [rendered-canvas dirties] (brush-core/render-stroke backup-canvas stroke)
        new-layer (assoc layer :canvas rendered-canvas)
        new-layers (util/replace-layer new-layer layers)]
    (.close layer-canvas)
    [(assoc-in record [:canvas-data :layers] new-layers)
     (change/make-raster-layer-dirty layer-id backup-canvas (.copy rendered-canvas) dirties layer-transform)]))

(defonce ^:private brush-stroke-cancel-action-key* :brush-stroke-cancel)
(defn brush-stroke-cancel-action-key []
  brush-stroke-cancel-action-key*)

(defn make-brush-stroke-cancel
  [layer-id canvas]
  (assoc (raster-layer/make-replace-raster-layer-canvas layer-id canvas)
    :type  (brush-stroke-cancel-action-key)))

(defmethod oplog/apply-action! (brush-stroke-cancel-action-key)
  [op record]
  (oplog/apply-action! (assoc op :type raster-layer/replace-raster-layer-canvas-action-key) record))
(ns top.kzre.krro.plugin.painting.core.undo.add-raster-layer
  (:require
    [top.kzre.krro.canvas.core.layer.util :as lu]
    [top.kzre.krro.core.reframe :as rf]
    [top.kzre.krro.plugin.painting.core.ops.snapshot :as snap]
    [top.kzre.krro.plugin.painting.core.ops.undo :as core]
    [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
    [top.kzre.krro.plugin.undo.core :as undo])
  (:import
   (top.kzre.krro.canvas.core.layer LayerUtils)
   [top.kzre.krro.util.tile TiledCanvas]))

(defn record-raster-layer-added!
  [canvas-id path layer]
  (let [^TiledCanvas canvas (:canvas layer)
        tile-size (.getTileSize canvas)
        tiles (.getTiles canvas)
        wrapped   (snap/wrap-tiled-canvas canvas)
        layers (pc/layers-by-id canvas-id)
        layer-transform (lu/layer-transform path layers)
        dirty-tiles (set (LayerUtils/transformTiles tiles tile-size layer-transform))]
    (undo/record-state!
      {:type          ::add-raster-layer
       :seq           (core/inc-undo-metadata-seq-key)
       :canvas-id     canvas-id
       :layer-id      (:id layer)
       :path          path
       :dirty-tiles   dirty-tiles
       :layer         (pc/persistable-layer layer)
       :snapshot      wrapped})))


(defmethod core/restore-canvas-state! [:after-undo ::add-raster-layer]
  [_ {:keys [canvas-id layer-id dirty-tiles]}]
  (rf/dispatch :krro.painting
               [:after-undo-add-raster-layer canvas-id layer-id dirty-tiles]))

(defmethod core/restore-canvas-state! [:before-redo ::add-raster-layer]
  [_ {:keys [canvas-id layer-id snapshot]}]
  (let [canvas (snap/read-tiled-canvas snapshot)]
    (rf/dispatch :krro.painting
                 [:before-redo-add-raster-layer layer-id canvas-id canvas])))

(defmethod core/restore-canvas-state! [:after-redo ::add-raster-layer]
  [_ {:keys [canvas-id dirty-tiles]}]
  (rf/dispatch :krro.painting
               [:after-redo-add-raster-layer canvas-id dirty-tiles]))
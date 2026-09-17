(ns top.kzre.krro.plugin.painting.core.changes.raster-layer
  (:require
   [top.kzre.krro.canvas.core.layer.util :as util]
   [top.kzre.krro.core.util.diff :as diff]
   [top.kzre.krro.plugin.painting.core.changes.composite :as composite]
   [top.kzre.krro.plugin.painting.core.oplog.protocol :as proto]
   [top.kzre.krro.plugin.painting.core.record :as record]
   [top.kzre.krro.plugin.painting.core.undo.core :as undo])
  (:import
    (java.lang AutoCloseable)
    (top.kzre.krro.util.tile TiledCanvas)))

(defrecord RasterLayerDirty
  [ layer-id ^TiledCanvas old-canvas ^TiledCanvas new-canvas dirty-tiles dirty-transform]
  AutoCloseable
  (close [_]
    (when old-canvas (.close old-canvas))
    (.close new-canvas))

  diff/IChange
  (seeds [_] layer-id)
  (empty-change? [_] (empty? dirty-tiles))
  (combine [this other] (composite/composite-change this other))

  proto/IOperation
  (realize [this record]
    (let [{:keys [layer layers]}
          (record/layer-context record layer-id)
          ^TiledCanvas dirty-canvas (:canvas layer)
          new-layer (assoc layer :canvas (.copy new-canvas))
          new-layers (util/replace-layer new-layer layers)]
      (.close dirty-canvas)
      ;; [record fx-v]
      [(assoc-in record [:canvas-data :layers] new-layers)
       [[:render-canvas this]]]))
  (record! [_ {:keys [canvas-id]}]
    (when (nil? old-canvas)
      (throw (ex-info "cannot record operation without old-canvas"
                      {:layer-id layer-id
                       :new-canvas new-canvas})))
    (undo/record-raster-layer-edited!
      ;; undo 系统只读
      canvas-id layer-id old-canvas new-canvas dirty-tiles dirty-transform)))

(defn make-raster-layer-dirty
  ([layer-id old-canvas new-canvas dirty-tiles dirty-transform]
   (->RasterLayerDirty layer-id old-canvas new-canvas dirty-tiles dirty-transform))
  ([layer-id new-canvas dirty-tiles dirty-transform]
   (->RasterLayerDirty layer-id nil new-canvas dirty-tiles dirty-transform))
  ([layer-id old-canvas new-canvas]
   (->RasterLayerDirty layer-id old-canvas new-canvas nil nil))
  ([layer-id new-canvas]
   (->RasterLayerDirty layer-id nil new-canvas nil nil)))
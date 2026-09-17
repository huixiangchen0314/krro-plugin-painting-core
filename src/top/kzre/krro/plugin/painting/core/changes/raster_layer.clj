(ns top.kzre.krro.plugin.painting.core.changes.raster-layer
  (:require
    [top.kzre.krro.canvas.core.layer.util :as util]
    [top.kzre.krro.core.util.diff :as diff]
    [top.kzre.krro.plugin.painting.core.record :as record])
  (:import
    [java.lang AutoCloseable]
    (java.util.function Function)
    (top.kzre.krro.util.tile TiledCanvas)))

(defrecord RasterLayerDirty
  [layer-id ^TiledCanvas old-canvas ^TiledCanvas new-canvas dirty-tiles dirty-transform]
  diff/IChange
  AutoCloseable
  (close [_]
    (when old-canvas (.close old-canvas))
    (.close new-canvas))
  Function
  (apply [_ record]
    (when-not (.isClosed new-canvas)
      (let [{:keys [layer layers]} (record/layer-context record)
            dirty-canvas (:canvas layer)
            new-canvas (assoc layer :canvas (.copy new-canvas))
            backup-layers    (util/replace-layer new-canvas layers)]
        (.close dirty-canvas)
        (assoc-in record [:canvas-data :layers] backup-layers)))))

(defn make-raster-layer-dirty
  [layer-id old-canvas new-canvas dirty-tiles dirty-transform]
  (->RasterLayerDirty layer-id old-canvas new-canvas dirty-tiles dirty-transform))
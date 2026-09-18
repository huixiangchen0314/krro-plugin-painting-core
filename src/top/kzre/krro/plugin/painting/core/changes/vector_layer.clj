(ns top.kzre.krro.plugin.painting.core.changes.vector-layer
  (:require
   [top.kzre.krro.canvas.core.layer.util :as util]
   [top.kzre.krro.core.util.diff :as diff]
   [top.kzre.krro.plugin.painting.core.changes.composite :as composite]
   [top.kzre.krro.plugin.painting.core.oplog.protocol :as proto]
   [top.kzre.krro.plugin.painting.core.undo.core :as undo]
   [top.kzre.krro.plugin.painting.core.project.vector-layer :as pv]
   [top.kzre.krro.plugin.painting.core.record :as record])
  (:import
   (java.lang AutoCloseable)))

(defrecord VectorLayerPathsDirty
  [layer-id old-paths new-paths dirty-tiles layer-transform]
  AutoCloseable
  (close [_] nil)

  diff/IChange
  (seeds [_] layer-id)
  (empty-change? [_] (= old-paths new-paths))
  (combine [this other] (composite/composite-change this other))

  proto/IOperation
  (realize [this record]
    (let [{:keys [canvas-id layer layers]} (record/layer-context record layer-id)
          new-layer  (pv/assoc-paths layer new-paths)
          new-layers (util/replace-layer new-layer layers)]
      [(assoc-in record [:canvas-data :layers] new-layers)
       [[:render-canvas canvas-id this]]]))
  (record! [_ record]
    (let [{:keys [canvas-id]} (record/layer-context record layer-id)]
      (undo/record-canvas-edited!
        canvas-id layer-id))))



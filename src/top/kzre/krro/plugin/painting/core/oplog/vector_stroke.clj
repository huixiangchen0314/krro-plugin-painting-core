(ns top.kzre.krro.plugin.painting.core.oplog.vector-stroke
  (:require
   [top.kzre.krro.brush.vector :as vec-brush]
   [top.kzre.krro.canvas.vector.core :as cv]
   [top.kzre.krro.canvas.vector.curve :as curve]
   [top.kzre.krro.core.reframe.core :as rf]
   [top.kzre.krro.plugin.painting.core.changes.path :as change]
   [top.kzre.krro.plugin.painting.core.record :as record]
   [top.kzre.krro.plugin.painting.core.store :as store])
  (:import
    (top.kzre.curve.bezier2d ArcLengthUtils Curve TableMapping)
    (top.kzre.krro.brush Stroke)))



(defn- vector-stroke->bezier-path
  [{:keys [^Curve curve width-samples t-params]} style]
  (let [arc-params (TableMapping/uniformSParams
                     (ArcLengthUtils/buildArcLengthParams curve (double-array t-params)))]
    {:path-type     :bezier
     :curve         (curve/curve->edn curve)
     :style         style
     :t-params      t-params
     :width-samples width-samples
     :arc-params    arc-params}))

(defn- render-stroke-to-path
  [^Stroke stroke style]
  (when-let [result (vec-brush/render-vector-stroke (.getStroke stroke))]
    (vector-stroke->bezier-path result style)))

(rf/reg-event-fx
  store/app-id :oplog/vector-stroke
  (fn [cofx [_ canvas-id layer-id  ^Stroke stroke style
             & {:keys [path-id]
                :or {path-id (cv/fresh-path-id)}
                :as ctx}]]
    (when (> (.size stroke) 2)
      (let [new-path (render-stroke-to-path stroke style)]
        (when new-path
          (let [{:keys [layer layer-transform]} (record/layer-context (:record cofx) layer-id)
                dirty-paths (cv/paths layer)
                op (change/make-vector-path-geometry-changed
                     layer-id path-id
                     (get dirty-paths path-id)
                     new-path layer-transform)]
            {:dispatch [:oplog/log canvas-id op ctx]}))))))

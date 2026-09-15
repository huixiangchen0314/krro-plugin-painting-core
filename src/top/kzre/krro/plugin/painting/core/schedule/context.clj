(ns top.kzre.krro.plugin.painting.core.schedule.context
  "上下文以及预处理"
  (:require
   [top.kzre.krro.plugin.painting.core.schedule.util :as util]
   [top.kzre.krro.plugin.painting.core.viewport :as vp])
  (:import
   [top.kzre.krro.util.math KMath]))


(defn diff-info [old-ctx new-ctx]
  (if old-ctx
    {:same-viewport? (= (:viewport old-ctx) (:viewport new-ctx))}
    {:same-viewport? false}))

(defn assoc-view-matrix [ctx old-ctx same-viewport?]
  (if same-viewport?
    (assoc ctx :view-matrix (:view-matrix old-ctx))
    (assoc ctx :view-matrix (vp/viewport->mat2d (:viewport ctx)))))

(defn assoc-view-dirty-tiles [ctx _old-ctx ]
  (let [{:keys [tile-size
                view-matrix viewport-w viewport-h
                dirty-tiles dirty-transform]} ctx
        transform-to-view
        (when (and view-matrix dirty-tiles dirty-transform)
          (KMath/mat2dMul view-matrix dirty-transform))
        view-dirty-tiles (util/dirty-region dirty-tiles transform-to-view
                                            viewport-w viewport-h tile-size)]
    (assoc ctx :view-dirty-tiles view-dirty-tiles)))

(defn diff
  [old-ctx new-ctx {:keys [same-viewport?]}]
  (-> new-ctx
      (assoc-view-matrix old-ctx same-viewport?)
      (assoc-view-dirty-tiles old-ctx)))


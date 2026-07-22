(ns top.kzre.krro.plugin.painting.core.brush.core
  (:import
   [top.kzre.colorutils.color RGB]))



(defonce default-brush
         {:dab          {:type :circle
                         :mask-type :hard
                         :radius 8.0}
          :color        (RGB/rgba 0.2 0.7 0.56 1.0)
          :dynamics     {:radius [{:sensor :pressure :curve :linear :min 0.5 :max 2.0 :mode :multiply}
                                  {:sensor :velocity :curve :linear :min 0.8 :max 1.2 :mode :multiply}]}
          :spacing      0.2
          :radius       3
          :blend-mode   :normal
          :mix-mode     :colored-brush
          ;:taper-start-px   50
          ;:taper-end-px     50
          ;:taper-fields     []
          })

(defonce global-brush (atom default-brush))

(defn set-global-brush! [brush]
  (reset! global-brush brush))
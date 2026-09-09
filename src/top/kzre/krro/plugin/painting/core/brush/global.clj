(ns top.kzre.krro.plugin.painting.core.brush.global
  (:import (top.kzre.colorutils.color RGB)))


(defonce default-brush
         {:dab          {:type :circle
                         :mask-type :hard}
          ;; 前景色
          :color        (RGB/rgba 0.2 0.3 0.56 0.65)
          ;; 动力学映射
          :dynamics     {:radius [{:sensor :pressure :curve :linear :min 0.5 :max 2.0 :mode :multiply}]}
          ;; DAB 间距
          :spacing      0.2
          :radius       3
          :blend-mode   :normal
          ;:taper-start-px   50
          ;:taper-end-px     50
          ;:taper-fields     []
          })

(defonce global-brush (atom default-brush))

(defn set-global-brush! [brush]
  (reset! global-brush brush))

(defn get-global-brush []
  (or @global-brush default-brush))
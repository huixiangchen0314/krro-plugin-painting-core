(ns top.kzre.plugin.painting.mode
  "定义极简绘图模式。"
  (:require [top.kzre.krro.core.mode :as mode]))

(def layout [:painting-canvas {:width 800 :height 600}])

(defn register! []
  (mode/register-mode!
    (mode/make-major-mode :painting "Painting"
                          :layout layout)))
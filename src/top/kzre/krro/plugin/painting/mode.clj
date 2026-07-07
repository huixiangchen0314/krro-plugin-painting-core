(ns top.kzre.krro.plugin.painting.mode
  "定义极简绘图模式。"
  (:require [top.kzre.krro.core.mode :as mode]
            [top.kzre.krro.core.plugin :as plugin]
            [top.kzre.krro.plugin.painting.canvas.core :as canvas]))

(def layout [:krro.painting/canvas {:krro.painting/canvas-width 800
                               :krro.painting/canvas-height 600}])

(defn register! []
  (plugin/register-plugin!
    {:id    :krro.painting/canvas-tag
     :type    :krro.plugin/javafx-tag
     :tag     :krro.painting/canvas
     :handler canvas/create-canvas})
  (mode/register-mode!
    (mode/make-major-mode :painting "Painting"
                          :layout layout)))
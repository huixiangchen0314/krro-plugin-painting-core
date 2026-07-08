(ns top.kzre.krro.plugin.painting.mode
  (:require
   [top.kzre.krro.core.frame :as frame]
   [top.kzre.krro.core.mode :as mode]
   [top.kzre.krro.core.plugin :as plugin]
   [top.kzre.krro.plugin.painting.canvas.core :as canvas]
   [top.kzre.krro.plugin.painting.spec :as spec]))

(defn layout-fn [f]
  ;; 从 Frame 参数获取默认画布尺寸，若未设置则使用默认值 800x600
  (let [w (or (frame/param f spec/canvas-width-key) 800)
        h (or (frame/param f spec/canvas-height-key) 600)]
    [:krro.painting/canvas {:krro.painting/canvas-width w
                            :krro.painting/canvas-height h}]))

(defn register! []
  (plugin/register-plugin!
    {:id    :krro.painting/canvas-tag
     :type    :krro.plugin/javafx-tag
     :tag     :krro.painting/canvas
     :handler canvas/create-canvas})
  (mode/register-mode!
    (mode/make-major-mode :painting "Painting"
                          :layout layout-fn)))
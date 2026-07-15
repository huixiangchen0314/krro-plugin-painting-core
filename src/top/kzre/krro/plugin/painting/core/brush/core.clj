(ns top.kzre.krro.plugin.painting.core.brush.core
  (:import
   [top.kzre.colorutils.color RGB]))



(defonce default-brush
         {:dab        {:type :circle :mask-type :hard :radius 8.0}
          :color      (RGB/rgba 0.0 0.0 1.0 1.0)
          :dynamics   {:pressure {:radius {:curve :linear :min 5.0 :max 15.0}}}
          :spacing    0.2
          :radius     8.0
          :blend-mode :normal
          :mix-mode   :default
          :taper-start 50    ;; 起笔渐隐像素长度
          :taper-end   50})  ;; 收笔渐隐像素长度

(defonce global-brush (atom default-brush))

(defn set-global-brush! [brush]
  (reset! global-brush brush))
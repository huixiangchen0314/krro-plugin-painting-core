(ns top.kzre.krro.plugin.painting.canvas.brush
  "画布默认笔刷定义。后续可扩展为笔刷管理器。"
  (:import (top.kzre.colorutils.color RGB)))

(def default-brush
  "默认笔刷配置：蓝色圆形硬笔尖，带压感动态半径。"
  {:dab        {:type :circle :mask-type :hard :radius 8.0}
   :color      (RGB/rgba (float 0.0) (float 0.0) (float 1.0) (float 1.0))
   :dynamics   {:pressure {:radius {:curve :linear :min 10.0 :max 50.0}}}
   :spacing    0.2
   :radius     8.0
   :blend-mode :normal
   :mix-mode   :default
   :taper-start 0.0
   :taper-end 0.0})
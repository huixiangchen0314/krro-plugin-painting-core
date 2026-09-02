(ns top.kzre.krro.plugin.painting.core.grid
  (:require
    [top.kzre.krro.core.frame :as frame]))

(defrecord Grid
  [visible        ;; 是否显示网格
   size           ;; 网格大小（画布逻辑坐标单位）
   offset-x       ;; X 方向偏移
   offset-y       ;; Y 方向偏移
   color          ;; 网格颜色 [r g b a]
   ])

(defonce default-grid
  (->Grid false
          10.0
          0.0
          0.0
          [0.5 0.5 0.5 0.5]))

(defn get-grid [frame]
  (or (frame/param frame ::grid)
      default-grid))
(ns top.kzre.krro.plugin.painting.core.layer.tiles)

(defmulti layer-tiles
          "获取图层占据的本地分块坐标"
          (fn [layer tile-size] (:type layer)))

;; 默认返回nil，表示无法计算.
(defmethod layer-tiles :default [_ _] nil)


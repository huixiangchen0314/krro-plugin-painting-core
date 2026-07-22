(ns top.kzre.krro.plugin.painting.core.tool.util
  "画笔工具通用辅助函数。"
  (:require [top.kzre.krro.canvas.core.layer.util :as layer-util])
  (:import (top.kzre.krro.canvas.core.layer MathUtils)
           (top.kzre.krro.plugin.painting.core.tool Util)))

(defn compute-total-inverse
  "计算图层 local 到 world 的总逆矩阵（可用于世界坐标 → 图层局部坐标）。
   返回 float[6] 仿射矩阵 [a b c d tx ty]。"
  [layer layers layer-path]
  (let [inv-local (layer-util/compose-inverse-transform layer)
        inv-parent (layer-util/parent-inverse-transform layers layer-path)]
    (if inv-parent
      (MathUtils/multiply (float-array inv-local) (float-array inv-parent))
      (float-array inv-local))))

(defn layer-dirties->world-dirties
  "将图层局部脏瓦片集合转换为世界坐标脏瓦片集合。
   若返回 nil，表示变换复杂或无法映射，调用方应视作全量刷新。
   layer       : 图层 map，可能包含 :x, :y, :scale-x, :scale-y, :rotation
   tile-size   : 瓦片尺寸（像素）
   local-dirties: 局部脏瓦片键集合（Set<Long>）"
  [layer tile-size local-dirties]
  (Util/localToWorldDirtyTiles
    (or local-dirties #{})
    (int tile-size)
    (double (or (:x layer) 0.0))
    (double (or (:y layer) 0.0))
    (double (or (:scale-x layer) 1.0))
    (double (or (:scale-y layer) 1.0))
    (double (or (:rotation layer) 0.0))))
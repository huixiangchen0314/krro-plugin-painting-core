(ns top.kzre.krro.plugin.painting.core.tool.util
  "画笔工具通用辅助函数。"
  (:require [top.kzre.krro.canvas.core.layer.util :as layer-util])
  (:import (top.kzre.krro.canvas.core.layer MathUtils)))

(defn compute-total-inverse
  "计算图层 local 到 world 的总逆矩阵（可用于世界坐标 → 图层局部坐标）。
   返回 float[6] 仿射矩阵 [a b c d tx ty]。"
  [layer layers layer-path]
  (let [inv-local (layer-util/compose-inverse-transform layer)
        inv-parent (layer-util/parent-inverse-transform layers layer-path)]
    (if inv-parent
      (MathUtils/multiply (float-array inv-local) (float-array inv-parent))
      (float-array inv-local))))

(defn transform-event
  "纯函数：将原始事件坐标变换到图层局部坐标系，并返回标准化事件及计算的逆矩阵。
   参数：
     ev          - 原始事件 map（至少包含 :x :y :type）
     layer       - 当前图层
     canvas-data - 画布全局数据（(:data ctx)，需包含 :layers）
   关键字参数（可选）：
     :parent-inv - 已缓存的父级逆矩阵，为 nil 时自动计算
   返回 map：
     {:event       - 标准化后的局部事件 map
      :parent-inv - 当前使用的逆矩阵，供调用方缓存}"
  [ev layer canvas-data & {:keys [parent-inv]}]
  (let [sensors {:pressure  (get ev :pressure 0.5)
                 :tilt-x    (get ev :tilt-x 0)
                 :tilt-y    (get ev :tilt-y 0)
                 :rotation  (get ev :rotation 0)
                 :timestamp (get ev :timestamp (System/currentTimeMillis))
                 :type      (:type ev)}
        inv (or parent-inv
                (let [layers     (:layers canvas-data)
                      layer-path (layer-util/find-layer-path (:id layer) layers)]
                  (compute-total-inverse layer layers layer-path)))
        pt  (layer-util/transform-point inv (:x ev) (:y ev))]
    {:event      (merge sensors {:x (:x pt), :y (:y pt)})
     :parent-inv inv}))
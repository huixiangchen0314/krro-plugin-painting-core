(ns top.kzre.krro.plugin.painting.core.edit.snap
  "吸附参数规格"
  (:require [clojure.spec.alpha :as s]))

;; 吸附基准
(s/def ::snap-base
  #{:active          ;; 活动选择，最后一个选择
    :closest         ;; 最近点
    :median          ;; 所选锚点的中心
    :individual})    ;; 各自吸附各自的

;; 吸附目标
(s/def ::snap-target
  #{:increment          ;; 画布逻辑空间步进
    :ruler              ;; 辅助尺
    :grid               ;; 视觉网格
    ;; 曲线，透视图层或矢量图层
    :anchor :segment :segment-center :segment-perpendicular})

(s/def ::snap-targets
  (s/coll-of ::snap-target :kind set? :into #{}))

(s/def ::transform #{:translate :rotate :scale})
(s/def ::transforms (s/coll-of ::transform :kind set? :into #{}))

(s/def ::angle-increment number?)
(s/def ::angle-increment-precision number?)
(s/def ::enabled boolean?)
(s/def ::increment-step number?)

;; 对齐到旋转目标
(s/def ::align-rotation boolean?)
;; 独立元素投影
(s/def ::project-individual boolean?)
;; 忽略不可选中的目标
(s/def ::ignore-unselectable boolean?)

(s/def ::snap-options
  (s/keys :req-un [::enabled
                   ::snap-base
                   ::snap-targets
                   ::transforms]
          :opt-un [::increment-step
                   ::angle-increment
                   ::angle-increment-precision
                   ::align-rotation
                   ::project-individual
                   ::ignore-unselectable]))
(ns top.kzre.krro.plugin.painting.core.edit.falloff
  "衰减编辑参数规格"
  (:require [clojure.spec.alpha :as s]
            [top.kzre.krro.curve.bezier2d.spec :as-alias bezier]))

;; ── 衰减曲线预设 ──
(s/def ::curve-preset
  #{:smooth          ;; 平滑（默认）：柔和自然，最通用
    :smoother        ;; 更平滑：中心区域更宽再逐渐变细
    :sphere          ;; 球状：圆润饱满凸起
    :root            ;; 根凸：中心隆起强，外围快速衰减
    :sharp           ;; 尖锐：中心陡峭，过渡偏硬
    :sharper         ;; 更锐利：中心点更压缩
    :linear          ;; 线性：均匀直线衰减
    :inverse-square  ;; 平方反比：只影响中心极小范围
    :constant        ;; 常量：半径内同等幅度变形
    :random})        ;; 随机：不规则起伏

(s/def ::falloff-space
  #{:world-space    ;; 世界空间衰减（固定逻辑坐标半径，视口缩放时屏幕半径变化）
    :screen-space}) ;; 屏幕空间衰减（固定屏幕像素半径，视口缩放时逻辑半径自动适应）

;; ── 自定义曲线 ──
(s/def ::custom-curve ::bezier/curve)

;; ── 衰减编辑选项 ──
(s/def ::falloff-options
  (s/keys :req-un [::enabled           ;; 是否启用衰减编辑
                   ::curve-preset]      ;; 衰减曲线预设
          :opt-un [::custom-curve       ;; 自定义曲线（当 preset 为 nil/:custom 或其他非法值时使用）
                   ::falloff-space      ;; 衰减形状，默认 :sphere
                   ::connected-only     ;; 仅相连项（Alt+O），通过拓扑连接传递
                   ::radius             ;; 影响半径
                   ::radius-step]))     ;; 半径精度/步进
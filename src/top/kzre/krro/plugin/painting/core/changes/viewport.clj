(ns top.kzre.krro.plugin.painting.core.changes.viewport
  "视口变化——三种独立语义。

   Pan     平移——缓存可复用——最轻
   Scale   缩放——需要重采样——中等
   Size    尺寸变化——缓存失效——最重

   三者不可合并——语义不同——失效代价不同。"
  (:require
   [top.kzre.krro.core.util.diff :as diff]
   [top.kzre.krro.plugin.painting.core.changes.composite :as composite]
   [top.kzre.krro.plugin.painting.core.schedule.context :as context]))

;; ═══════════════════════════════════════════════
;; ViewportPan——平移
;; ═══════════════════════════════════════════════

(defrecord ViewportPan []
  diff/IChange
  (seeds [_] (context/context-key))
  (combine [this other]
    (cond
      (diff/empty-change? other)  this
      (instance? ViewportPan other)       this   ;; 连续平移——最新覆盖
      :else                               (composite/->CompositeChange [this other])))
  (empty-change? [_] false))

;; ═══════════════════════════════════════════════
;; ViewportScale——缩放
;; ═══════════════════════════════════════════════

(defrecord ViewportScale []
  diff/IChange
  (seeds [_] (context/context-key))
  (combine [this other]
    (cond
      (diff/empty-change? other)  this
      (instance? ViewportScale other)     this   ;; 连续缩放——最新覆盖
      :else                               (composite/->CompositeChange [this other])))
  (empty-change? [_] false))

;; ═══════════════════════════════════════════════
;; ViewportSize——尺寸变化
;; ═══════════════════════════════════════════════

(defrecord ViewportSize []
  diff/IChange
  (seeds [_] (context/context-key))
  (combine [this other]
    (cond
      (diff/empty-change? other)  this
      (instance? ViewportSize other)      this   ;; 连续尺寸变——最新覆盖
      :else                               (composite/->CompositeChange [this other])))
  (empty-change? [_] false))
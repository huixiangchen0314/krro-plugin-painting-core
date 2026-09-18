(ns top.kzre.krro.plugin.painting.core.changes.viewport
  (:require
    [top.kzre.krro.core.util.diff :as diff]
    [top.kzre.krro.plugin.painting.core.changes.composite :as composite]))

(defrecord ViewportPan []
  diff/IChange
  (seeds [_] :context)
  (combine [this other]
    (cond
      (diff/empty-change? other)  this
      (instance? ViewportPan other)       this   ;; 连续平移——最新覆盖
      :else                               (composite/->CompositeChange [this other])))
  (empty-change? [_] false))

(defrecord ViewportScale []
  diff/IChange
  (seeds [_] :context)
  (combine [this other]
    (cond
      (diff/empty-change? other)  this
      (instance? ViewportScale other)     this   ;; 连续缩放——最新覆盖
      :else                               (composite/->CompositeChange [this other])))
  (empty-change? [_] false))

(defrecord ViewportResize []
  diff/IChange
  (seeds [_] :context)
  (combine [this other]
    (cond
      (diff/empty-change? other)  this
      (instance? ViewportResize other)      this   ;; 连续尺寸变——最新覆盖
      :else                               (composite/->CompositeChange [this other])))
  (empty-change? [_] false))

(defrecord ViewportRefreshed [])

(defn refreshed
  "视口全刷新，例如初始化渲染，换画布"
  []
  (->ViewportRefreshed))

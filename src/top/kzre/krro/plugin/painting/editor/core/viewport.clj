(ns top.kzre.krro.plugin.painting.editor.core.viewport
  "视口定义与坐标转换。"
  (:require [top.kzre.krro.core.frame :as frame]))

(defrecord ViewPort
  [^double offset-x   ;; 视口左上角在逻辑空间中的 X 坐标
   ^double offset-y   ;; 视口左上角在逻辑空间中的 Y 坐标
   ^double zoom])     ;; 缩放比例，>1 放大，<1 缩小

(def default-viewport
  "默认视口：零偏移，100% 缩放。"
  (->ViewPort 0.0 0.0 1.0))

(defn screen->logic
  "将屏幕坐标 (sx, sy) 转换为逻辑坐标。"
  [^ViewPort vp sx sy]
  {:x (+ (/ (double sx) (:zoom vp)) (:offset-x vp))
   :y (+ (/ (double sy) (:zoom vp)) (:offset-y vp))})

(defn logic->screen
  "将逻辑坐标 (lx, ly) 转换为屏幕坐标。"
  [^ViewPort vp lx ly]
  {:x (* (- (double lx) (:offset-x vp)) (:zoom vp))
   :y (* (- (double ly) (:offset-y vp)) (:zoom vp))})

(def viewport-param-key ::viewport)
(defn get-viewport [frame]
  (or (frame/param frame viewport-param-key) default-viewport))

(defn set-viewport! [frame viewport]
  (frame/set-param! frame viewport-param-key viewport))

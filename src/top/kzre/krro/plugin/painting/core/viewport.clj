(ns top.kzre.krro.plugin.painting.core.viewport
  "视口定义与坐标转换。"
  (:require
    [top.kzre.krro.core.frame :as frame])
  (:import
    (top.kzre.krro.util.math KMath)))

(defrecord ViewPort
  [^double offset-x   ;; 视口左上角在逻辑空间中的 X 坐标
   ^double offset-y   ;; 视口左上角在逻辑空间中的 Y 坐标
   ^double zoom])     ;; 缩放比例，>1 放大，<1 缩小

(defonce ^:private default-viewport* (->ViewPort 0.0 0.0 1.0))

(defn make-viewport
  ([] default-viewport*))

(defn identity-viewport
  [] (make-viewport))

;; ── 坐标转换 ──────────────────────────────────
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


(def ^:private frame-key* ::viewport)

(defn frame-key [] frame-key*)

(defn set-viewport! [frame viewport]
  {:pre [(some? viewport)]}
  (frame/set-param! frame (frame-key) viewport))

(defn get-viewport
  "这个假定一个 frame 一个 viewport，虽然这个假定是对的，但不该放在这里"
  [frame]
  (or (frame/param frame (frame-key)) (make-viewport)))

(defn viewport->mat2d [vp]
  (let [zoom (:zoom vp)
        offx (:offset-x vp)
        offy (:offset-y vp)]
    (KMath/mat2dCompose (- (* offx zoom)) (- (* offy zoom)) zoom zoom 0)))


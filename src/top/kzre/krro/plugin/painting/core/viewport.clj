(ns top.kzre.krro.plugin.painting.core.viewport
  "视口定义与坐标转换。"
  (:require [top.kzre.krro.core.frame :as frame]))

(defrecord ViewPort
  [^double offset-x   ;; 视口左上角在逻辑空间中的 X 坐标
   ^double offset-y   ;; 视口左上角在逻辑空间中的 Y 坐标
   ^double zoom])     ;; 缩放比例，>1 放大，<1 缩小

(def default-viewport
  "默认视口：零偏移，100% 缩放。"
  (->ViewPort 0.0 0.0 1.0))

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

;; ── 状态管理 ──────────────────────────────────
(def viewport-param-key ::viewport)

(defn get-viewport [frame]
  (or (frame/param frame viewport-param-key) default-viewport))

(defn set-viewport! [frame viewport]
  (frame/set-param! frame viewport-param-key viewport))

;; ── 平移工具函数 ─────────────────────────────
(defn pan!
  "平移视口：dx, dy 为屏幕像素位移。"
  [frame dx dy]
  (let [vp (get-viewport frame)
        zoom (:zoom vp)
        new-offset-x (- (:offset-x vp) (/ dx zoom))
        new-offset-y (- (:offset-y vp) (/ dy zoom))]
    (set-viewport! frame (assoc vp
                           :offset-x new-offset-x
                           :offset-y new-offset-y))))

(defn pan-to!
  "将视口左上角移动到指定的逻辑坐标 (x, y)。"
  [frame x y]
  (let [vp (get-viewport frame)]
    (set-viewport! frame (assoc vp :offset-x (double x) :offset-y (double y)))))

;; ── 缩放工具函数 ─────────────────────────────
(defn zoom!
  "以画布中心为基准缩放视口，factor 为缩放倍率（>1 放大，<1 缩小），
   例如 (zoom! frame 1.1)。"
  [frame factor]
  (let [vp (get-viewport frame)
        new-zoom (-> (:zoom vp) (* factor) (max 0.01) (min 100.0))
        ; 保持中心点不变需要知道画布尺寸，这里采用简单偏移补偿：
        ; 没有画布尺寸信息，暂时不做中心补偿，直接改变 zoom
        ; 如需中心缩放，请在外部使用 zoom-at-point!
        ]
    (set-viewport! frame (assoc vp :zoom new-zoom))))

(defn zoom-at-point!
  "以屏幕坐标 (sx, sy) 为中心缩放视口，factor 为缩放倍率。
   通常用于鼠标滚轮缩放。"
  [frame sx sy factor]
  (let [vp (get-viewport frame)
        old-zoom (:zoom vp)
        new-zoom (-> old-zoom (* factor) (max 0.01) (min 100.0))
        ;; 计算鼠标指向的逻辑坐标
        lx (+ (/ sx old-zoom) (:offset-x vp))
        ly (+ (/ sy old-zoom) (:offset-y vp))
        ;; 新视口左上角应为 lx - sx / new-zoom
        new-offset-x (- lx (/ sx new-zoom))
        new-offset-y (- ly (/ sy new-zoom))]
    (set-viewport! frame (assoc vp
                           :zoom new-zoom
                           :offset-x new-offset-x
                           :offset-y new-offset-y))))

(defn reset-viewport!
  "重置视口为默认状态（偏移 0,0，缩放 1.0）。"
  [frame]
  (set-viewport! frame default-viewport))

(ns top.kzre.krro.plugin.painting.core.event
  "标准化画布输入事件。Pointer (鼠标) 与 Pen (数位笔) 事件规范分开定义。
   Pen 事件基于 pen4j 的 PenState 设计，仅提取绘画所需的关键数据。
   使用 multi-spec 根据 :device-type 动态分派。"
  (:require [clojure.spec.alpha :as s]))

;; ═══════════════════════════════════════════════════════
;; 公共字段定义
;; ═══════════════════════════════════════════════════════
(s/def ::x number?)
(s/def ::y number?)
(s/def ::pressure (s/double-in :min 0.0 :max 1.0))
(s/def ::timestamp pos-int?)
(s/def ::pointer-id nat-int?)

;; ── 修饰键 ──────────────────────────────────────────
(s/def ::ctrl boolean?)
(s/def ::shift boolean?)
(s/def ::alt boolean?)
(s/def ::modifiers (s/keys :opt-un [::ctrl ::shift ::alt]))

;; ── 设备类型 ────────────────────────────────────────
(s/def ::device-type #{:pointer :pen})

;; ═══════════════════════════════════════════════════════
;; Pointer 事件 (鼠标)
;; ═══════════════════════════════════════════════════════
(s/def ::pointer-event-type #{:press :drag :release :click :double-click :move :scroll})
(s/def ::mouse-button #{:left :middle :right}) ;; 鼠标按键
(s/def ::delta-x double?)
(s/def ::delta-y double?)

(s/def ::pointer-event
  (s/keys :req-un [::device-type ::pointer-event-type ::x ::y ::pressure ::timestamp ::pointer-id]
          :opt-un [::modifiers ::mouse-button ::delta-x ::delta-y]))

;; ═══════════════════════════════════════════════════════
;; Pen 事件 (数位笔)
;; ═══════════════════════════════════════════════════════
(s/def ::pen-event-type #{:press :drag :release :hover})

(s/def ::tilt-x (s/double-in :min -1.0 :max 1.0))
(s/def ::tilt-y (s/double-in :min -1.0 :max 1.0))
(s/def ::twist (s/double-in :min 0.0 :max 360.0))
(s/def ::near boolean?)

(s/def ::pen-event
  (s/keys :req-un [::device-type ::pen-event-type ::x ::y ::pressure ::timestamp ::pointer-id
                   ::tilt-x ::tilt-y ::twist ::near]
          :opt-un [::modifiers]))

;; ═══════════════════════════════════════════════════════
;; 多方法分派（用于 multi-spec）
;; ═══════════════════════════════════════════════════════
(defmulti event-spec :device-type)
(defmethod event-spec :pointer [_] ::pointer-event)
(defmethod event-spec :pen [_] ::pen-event)

;; ═══════════════════════════════════════════════════════
;; 综合事件 Spec（使用 multi-spec）
;; ═══════════════════════════════════════════════════════
(s/def ::event
  (s/multi-spec event-spec :device-type))

;; ═══════════════════════════════════════════════════════
;; 构造器
;; ═══════════════════════════════════════════════════════
(def default-modifiers {:ctrl false :shift false :alt false})

(defn make-pointer-event
  "创建鼠标事件。type, x, y 为必选参数，其余为关键字可选。
   (make-pointer-event :press 100 200)
   (make-pointer-event :drag 150 250 :timestamp custom-ts :mouse-button :right :modifiers {...})
   (make-pointer-event :scroll 0 0 :delta-x 0.0 :delta-y -2.5)"
  [type x y & {:keys [timestamp pointer-id modifiers pressure mouse-button delta-x delta-y]
               :or   {timestamp  (System/currentTimeMillis)
                      pointer-id 0
                      modifiers  default-modifiers
                      pressure   (case type
                                   :press 1.0
                                   :scroll 0.0
                                   0.5)
                      mouse-button :left
                      delta-x 0.0
                      delta-y 0.0}}]
  {:device-type :pointer
   :type        type
   :x           (double x)
   :y           (double y)
   :pressure    (double pressure)
   :timestamp   timestamp
   :pointer-id  pointer-id
   :modifiers   modifiers
   :mouse-button mouse-button
   :delta-x (double delta-x)
   :delta-y (double delta-y)
   })

(defn make-pen-event
  "创建标准笔事件。type, x, y, pressure, tilt-x, tilt-y, twist, near? 为必选参数。
   (make-pen-event :press 200 300 0.8 0.2 -0.3 45.0 true)
   (make-pen-event :hover 200 300 0.0 0.1 0.1 0.0 true :pointer-id 1)"
  [type x y pressure tilt-x tilt-y twist near?
   & {:keys [timestamp pointer-id modifiers]
      :or   {timestamp  (System/currentTimeMillis)
             pointer-id 0
             modifiers  default-modifiers}}]
  {:device-type :pen
   :type        type
   :x           (double x)
   :y           (double y)
   :pressure    (double pressure)
   :tilt-x      (double tilt-x)
   :tilt-y      (double tilt-y)
   :twist       (double twist)
   :near        (boolean near?)
   :timestamp   timestamp
   :pointer-id  pointer-id
   :modifiers   modifiers})

;; ═══════════════════════════════════════════════════════
;; 验证辅助
;; ═══════════════════════════════════════════════════════
(defn valid? [event]
  (s/valid? ::event event))

(defn explain [event]
  (s/explain ::event event))
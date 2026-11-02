(ns top.kzre.krro.plugin.painting.core.event
  "标准化画布输入事件。Pointer (鼠标) 与 Pen (数位笔) 事件规范分开定义。
   使用 multi-spec 根据 :device-type 动态分派。

   字段命名约定：所有事件共用一个 :type 字段（:press/:drag/:release/...），
   设备特定的合法值集由 s/and 追加约束，而不是用 :pointer-event-type /
   :pen-event-type 两个不同的 key——后者会让构造器、分派、消费者都
   被迫处理两种 key，没有收益。"
  (:require [clojure.spec.alpha :as s]))

;; ═══════════════════════════════════════════════════════
;; 公共字段
;; ═══════════════════════════════════════════════════════
(s/def ::x number?)
(s/def ::y number?)
(s/def ::pressure (s/double-in :min 0.0 :max 1.0))
(s/def ::timestamp pos-int?)
(s/def ::pointer-id nat-int?)
(s/def ::type keyword?)                              ; 事件类型，值域由设备约束

(s/def ::ctrl boolean?)
(s/def ::shift boolean?)
(s/def ::alt boolean?)
(s/def ::modifiers (s/keys :opt-un [::ctrl ::shift ::alt]))

(s/def ::device-type #{:pointer :pen})

;; ═══════════════════════════════════════════════════════
;; Pointer 事件
;; ═══════════════════════════════════════════════════════
(s/def ::pointer-type #{:press :drag :release :click :double-click :move :scroll :over})
(s/def ::mouse-button #{:left :middle :right})
(s/def ::delta-x double?)
(s/def ::delta-y double?)

(s/def ::pointer-event
  (s/and
    (s/keys :req-un [::device-type ::type ::x ::y ::pressure ::timestamp ::pointer-id]
            :opt-un [::modifiers ::mouse-button ::delta-x ::delta-y])
    #(= :pointer (:device-type %))
    #(contains? #{:press :drag :release :click :double-click :move :scroll :over}
                (:type %))))

;; ═══════════════════════════════════════════════════════
;; Pen 事件
;; ═══════════════════════════════════════════════════════
(s/def ::pen-type #{:press :drag :release :hover})
(s/def ::tilt-x (s/double-in :min -1.0 :max 1.0))
(s/def ::tilt-y (s/double-in :min -1.0 :max 1.0))
(s/def ::twist (s/double-in :min 0.0 :max 360.0))
(s/def ::near boolean?)

(s/def ::pen-event
  (s/and
    (s/keys :req-un [::device-type ::type ::x ::y ::pressure ::timestamp ::pointer-id
                     ::tilt-x ::tilt-y ::twist ::near]
            :opt-un [::modifiers])
    #(= :pen (:device-type %))
    #(contains? #{:press :drag :release :hover} (:type %))))

;; ═══════════════════════════════════════════════════════
;; multi-spec
;; ═══════════════════════════════════════════════════════
(defmulti event-spec :device-type)
(defmethod event-spec :pointer [_] ::pointer-event)
(defmethod event-spec :pen     [_] ::pen-event)

(s/def ::event (s/multi-spec event-spec :device-type))

;; ═══════════════════════════════════════════════════════
;; 构造器
;; ═══════════════════════════════════════════════════════
(def default-modifiers {:ctrl false :shift false :alt false})

(defn make-pointer-event
  "创建鼠标事件。type, x, y 为必选参数，其余为关键字可选。

   :mouse-button 的默认值按 type 分支：
     :scroll   —— 无按钮事件，不设置 :mouse-button（保持 nil）
     :move     —— 无按钮事件，不设置
     其他      —— 默认 :left

   滚轮事件的 delta-y 约定：向上滚为正，与 JavaFX 的 deltaY 同号
   （JavaFX 向上滚 deltaY 为正）。输入层不做取反。"
  [type x y & {:keys [timestamp pointer-id modifiers pressure mouse-button delta-x delta-y]
               :or   {timestamp  (System/currentTimeMillis)
                      pointer-id 0
                      modifiers  default-modifiers
                      pressure   (case type
                                   :press 1.0
                                   :drag  1.0
                                   :release 0.0
                                   0.0)
                      delta-x 0.0
                      delta-y 0.0}}]
  (cond-> {:device-type  :pointer
           :type         type
           :x            (double x)
           :y            (double y)
           :pressure     (double pressure)
           :timestamp    timestamp
           :pointer-id   pointer-id
           :modifiers    modifiers
           :delta-x      (double delta-x)
           :delta-y      (double delta-y)}
          ;; 只有按压类事件带按钮；move/scroll/over 不带
          (and mouse-button (not (#{:move :scroll :over} type)))
          (assoc :mouse-button mouse-button)

          ;; 默认按钮：非无按钮事件，默认左键
          (and (nil? mouse-button) (not (#{:move :scroll :over} type)))
          (assoc :mouse-button :left)))

(defn make-pen-event
  "创建标准笔事件。"
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
;; 验证
;; ═══════════════════════════════════════════════════════
(defn valid?  [event] (s/valid? ::event event))
(defn explain [event] (s/explain ::event event))
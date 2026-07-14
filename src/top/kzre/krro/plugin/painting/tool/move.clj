(ns top.kzre.krro.plugin.painting.tool.move
  "移动工具：拖拽修改当前图层的平移属性 (x, y)。"
  (:require [top.kzre.krro.plugin.painting.tool.protocol :as tp]
            [top.kzre.krro.core.custom :as custom]))

(custom/defcustom :krro.painting/move-tool-speed
                  1.0
                  :type :number
                  :group :krro.painting/edit
                  :doc "移动工具的移动速度配置, 1.0 为像素同比速度.")

(custom/defcustom :krro.painting/move-tool-dead-zone
                  0.5
                  :type :number
                  :group :krro.painting/edit
                  :doc "移动工具的死区（像素），位移小于此值时忽略移动。")

(defrecord MoveTool [initial-mouse   ;; atom: {:x :y}
                     last-event     ;; atom: 最近鼠标事件 {:x :y}
                     initial-layer] ;; atom: {:x :y} 按下时图层的平移
  tp/ITool
  (begin! [_ layer ctx]
    (reset! initial-mouse nil)
    (reset! last-event nil)
    (reset! initial-layer nil)
    layer)

  (end! [_ layer ctx] layer)

  (apply! [_ layer ev ctx]
    (case (:type ev)
      :press   (do (reset! initial-mouse {:x (:x ev) :y (:y ev)})
                   (reset! last-event ev)
                   (reset! initial-layer {:x (or (:x layer) 0.0) :y (or (:y layer) 0.0)})
                   :start)
      :drag    (do (reset! last-event ev) :continue)
      :release :commit
      :idle))

  (preview! [_ layer ctx]
    (if-let [init-mouse @initial-mouse]
      (if-let [last-ev @last-event]
        (let [init-layer @initial-layer
              speed      (custom/get-custom :krro.painting/move-tool-speed (:frame ctx))
              dead-zone  (custom/get-custom :krro.painting/move-tool-dead-zone (:frame ctx))
              dx         (* speed (- (:x last-ev) (:x init-mouse)))
              dy         (* speed (- (:y last-ev) (:y init-mouse)))]
          (if (and (< (Math/abs (double dx)) dead-zone)
                   (< (Math/abs (double dy)) dead-zone))
            layer   ;; 忽略微小移动，返回传入的图层（不变）
            (let [new-x (+ (or (:x init-layer) 0.0) dx)
                  new-y (+ (or (:y init-layer) 0.0) dy)]
              (assoc layer :x new-x :y new-y))))
        layer)
      layer))

  (commit! [this layer ctx]
    (let [final (tp/preview! this layer ctx)]
      (reset! initial-mouse nil)
      (reset! last-event nil)
      (reset! initial-layer nil)
      final)))

(defn make-move-tool [] (->MoveTool (atom nil) (atom nil) (atom nil)))
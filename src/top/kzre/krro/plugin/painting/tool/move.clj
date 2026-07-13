(ns top.kzre.krro.plugin.painting.tool.move
  "移动工具：拖拽修改当前图层的平移属性 (x, y)。"
  (:require [top.kzre.krro.plugin.painting.tool.protocol :as tp]))

(defrecord MoveTool [initial-mouse   ;; atom: {:x :y}
                     last-event]     ;; atom: 最近鼠标事件 {:x :y}
  tp/ITool
  (begin! [_ layer ctx]
    (reset! initial-mouse nil)
    (reset! last-event nil)
    layer)

  (end! [_ layer ctx] layer)

  (apply! [_ layer ev ctx]
    (case (:type ev)
      :press   (do (reset! initial-mouse {:x (:x ev) :y (:y ev)})
                   (reset! last-event ev)
                   :start)
      :drag    (do (reset! last-event ev) :continue)
      :release :commit
      :idle))

  (preview! [_ layer ctx]
    (if-let [init @initial-mouse]
      (if-let [last-ev @last-event]
        (let [dx (- (:x last-ev) (:x init))
              dy (- (:y last-ev) (:y init))
              new-x (+ (or (:x layer) 0.0) dx)
              new-y (+ (or (:y layer) 0.0) dy)]
          (assoc layer :x new-x :y new-y))
        layer)
      layer))

  (commit! [this layer ctx]
    (let [final (tp/preview! this layer ctx)]
      (reset! initial-mouse nil)
      (reset! last-event nil)
      final)))

(defn make-move-tool [] (->MoveTool (atom nil) (atom nil)))
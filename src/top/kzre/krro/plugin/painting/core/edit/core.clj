(ns top.kzre.krro.plugin.painting.core.edit.core
  (:require
    [top.kzre.krro.plugin.painting.core.edit.effects]
    [top.kzre.krro.plugin.painting.core.edit.viewport]
   [top.kzre.krro.plugin.painting.core.edit.dispatch :as dispatch]
    [top.kzre.krro.plugin.painting.core.edit.brush]
    [top.kzre.krro.plugin.painting.core.edit.vector-brush]
    [top.kzre.krro.plugin.painting.core.edit.fill]
    [top.kzre.krro.plugin.painting.core.edit.anchor]
   [top.kzre.krro.plugin.painting.core.edit.move]))


(defmethod dispatch/tool-event :move
  [_ event-map]
  (case (:type event-map)
    :press :move-tool/press
    :drag :move-tool/drag
    :release :move-tool/release
    nil))

(defmethod dispatch/tool-event :viewport
  [_ event-map]
  (case (:type event-map)
    (case (:type event-map)
      :press :viewport-tool/press
      :drag :viewport-tool/drag
      :scroll :viewport-tool/scroll
      nil)))

(defmethod dispatch/tool-event :brush
  [_ event-map]
  (case (:type event-map)
    :press :brush-tool/press
    :drag :brush-tool/drag
    :release :brush-tool/release
    :move :brush-tool/move
    nil))

(defmethod dispatch/tool-event :vector-brush
  [_ event-map]
  (case (:type event-map)
    :press :vector-brush-tool/press
    :drag :vector-brush-tool/drag
    :release :vector-brush-tool/release
    nil))

(defmethod dispatch/tool-event :fill
  [_ event-map]
  (case (:type event-map)
    :press :fill-tool/press
    nil))

(defmethod dispatch/tool-event :anchor-translate
  [_ event-map]
  (case (:type event-map)
    :press :anchor-translate/press
    :drag :anchor-translate/drag
    :release :anchor-translate/release
    nil))
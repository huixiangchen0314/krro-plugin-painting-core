(ns top.kzre.krro.plugin.painting.core.edit.core
  (:require
    [top.kzre.krro.plugin.painting.core.edit.viewport]
   [top.kzre.krro.plugin.painting.core.edit.dispatch :as dispatch]
   [top.kzre.krro.plugin.painting.core.edit.move]))


(defmethod dispatch/tool-event [:move :press]   [_ _] :move-tool/press)
(defmethod dispatch/tool-event [:move :drag]    [_ _] :move-tool/drag)
(defmethod dispatch/tool-event [:move :release] [_ _] :move-tool/release)
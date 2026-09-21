(ns top.kzre.krro.plugin.painting.core.core
  (:require [top.kzre.krro.core.plugin :as plugin]
            [top.kzre.krro.plugin.painting.core.canvas.core]
            [top.kzre.krro.plugin.painting.core.changes.core]
            [top.kzre.krro.plugin.painting.core.edit.core]
            [top.kzre.krro.plugin.painting.core.session.core]
            [top.kzre.krro.plugin.painting.core.layer.core]
            [top.kzre.krro.plugin.painting.core.oplog.core]
            [top.kzre.krro.plugin.painting.core.ops.undo :as undo]
            [top.kzre.krro.plugin.painting.core.project.core]
            [top.kzre.krro.plugin.painting.core.project.raster-layer :as pr]
            [top.kzre.krro.plugin.painting.core.schedule.core]
            [top.kzre.krro.plugin.painting.core.tool.registry]))

(defn init []
  (plugin/reg-plugin! pr/tiled-canvas-codec-plugin-def)
  (undo/init-undo-hooks!)
  )


(plugin/reg-plugin! {:name :krro.plugin/painting :mount init})
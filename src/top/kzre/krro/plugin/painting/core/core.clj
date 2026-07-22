(ns top.kzre.krro.plugin.painting.core.core
  (:require [top.kzre.krro.core.plugin :as plugin]
            [top.kzre.krro.plugin.painting.core.ops.undo :as undo]
            [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
            [top.kzre.krro.plugin.painting.core.project.raster-layer :as pr]))


(defn init []
  (plugin/register-plugin! pc/canvas-codec-plugin-def)
  (plugin/register-plugin! pr/tiled-canvas-codec-plugin-def)
  (undo/init-undo-hooks!)
  )


(plugin/register-plugin! {:name :krro.plugin/painting :init init})
(ns top.kzre.krro.plugin.painting.editor.core.core
  "绘图插件入口。"
  (:require
    [top.kzre.krro.canvas.core.core]
    [top.kzre.krro.canvas.raster.core :as rc]
    [top.kzre.krro.canvas.vector.core]
    [top.kzre.krro.core.plugin :as plugin]
    [top.kzre.krro.plugin.painting.core.ops.undo :as undo]
    [top.kzre.krro.plugin.painting.core.command.core]
    [top.kzre.krro.plugin.painting.editor.core.mode :as mode]
    [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
    [top.kzre.krro.plugin.undo.core]
    [top.kzre.krro.ui.javafx.core]))

(defn init []
  (rc/use-raster-merge-layer!)
  (plugin/register-plugin! pc/canvas-codec-plugin-def)
  (mode/register!)
  (undo/init-undo-hooks!)
  )


(plugin/register-plugin! {:name :krro.plugin/painting :init init})
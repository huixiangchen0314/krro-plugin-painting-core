(ns top.kzre.krro.plugin.painting.core
  "绘图插件入口。"
  (:require
    [top.kzre.krro.canvas.core.core]
    [top.kzre.krro.core.project :as proj]
    [top.kzre.krro.plugin.painting.canvas.undo :as undo]
    [top.kzre.krro.plugin.undo.core]
    [top.kzre.krro.canvas.raster.core :as rc]
    [top.kzre.krro.canvas.vector.core]
    [top.kzre.krro.core.plugin :as plugin]
    [top.kzre.krro.plugin.painting.canvas.project :as canvas-proj]
    [top.kzre.krro.plugin.painting.commands]
    [top.kzre.krro.plugin.painting.mode :as mode]
    [top.kzre.krro.ui.javafx.core]))

(defn init []
  (proj/register-protected-key! :krro.painting/raster)
  (rc/use-raster-merge-layer!)
  (plugin/register-plugin! canvas-proj/canvas-codec-plugin-def)
  (plugin/register-plugin! canvas-proj/layer-meta-codec-plugin-def)
  (mode/register!)
  (undo/init-undo-hooks!)
  )


(plugin/register-plugin! {:name :krro.plugin/painting :init init})
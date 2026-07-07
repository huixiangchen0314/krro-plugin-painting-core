(ns top.kzre.krro.plugin.painting.core
  "绘图插件入口。"
  (:require
    [top.kzre.krro.ui.javafx.core]
    [top.kzre.krro.canvas.core.core]
    [top.kzre.krro.canvas.raster.core]
    [top.kzre.krro.canvas.vector.core]
   [top.kzre.krro.core.plugin :as plugin]
    [top.kzre.krro.plugin.painting.canvas.project :as canvas-proj]
   [top.kzre.krro.plugin.painting.mode :as mode]))



(defn init []
  ;; 2. 注册绘图模式
  (plugin/register-plugin! canvas-proj/canvas-codec-plugin-def)
  (mode/register!)
  )


(plugin/register-plugin! {:name :krro.plugin/painting :init init})
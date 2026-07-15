(ns top.kzre.krro.plugin.painting.core.command.brush
  (:require [top.kzre.krro.core.command :as cmd]
            [top.kzre.krro.plugin.painting.core.brush.core  :as brush]))



(cmd/register-command!
  :krro.painting/set-global-brush
  (fn [project new-brush]
    (brush/set-global-brush! new-brush)
    project)
  :description "设置全局笔刷（参数为笔刷 map）")
(ns top.kzre.plugin.painting.core
  "绘图插件入口。"
  (:require
   [top.kzre.krro.core.plugin :as plugin]
   [top.kzre.plugin.painting.canvas :as canvas]
   [top.kzre.plugin.painting.mode :as mode]))

(plugin/register-plugin!
  {:name :krro.plugin/painting-canvas
   :type :krro.plugin/javafx-tag
   :tag  :painting-canvas
   :handler canvas/create-canvas})

(defn init []
  ;; 2. 注册绘图模式
  (mode/register!))


(plugin/register-plugin! {:name :krro.plugin/painting :init init})
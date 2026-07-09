(ns top.kzre.krro.plugin.painting.mode
  "绘画模式定义，使用新的 define-major-mode 宏。"
  (:require
    [top.kzre.krro.core.core :as core]                  ;; 引入 define-major-mode 宏
    [top.kzre.krro.core.frame :as frame]
    [top.kzre.krro.core.plugin :as plugin]
    [top.kzre.krro.plugin.painting.canvas.core :as canvas]
    [top.kzre.krro.plugin.painting.spec :as spec]))

(defn layout-fn
  "动态获取画布尺寸，从 Frame 参数回退到默认值 800x600。"
  [f]
  (let [w (or (frame/param f spec/canvas-width-key) 800)
        h (or (frame/param f spec/canvas-height-key) 600)]
    [:krro.painting/canvas {:krro.painting/canvas-width w
                            :krro.painting/canvas-height h}]))

;; 使用宏定义绘画主模式，自动注册模式 spec 并生成 activate/deactivate 命令
(core/defmajor :krro.painting/painting "Painting Mode."
                        :layout layout-fn)

(defn register!
  "注册画布标签到 JavaFX 标签系统。"
  []
  (plugin/register-plugin!
    {:id    :krro.painting/canvas-tag
     :type    :krro.plugin/javafx-tag
     :tag     :krro.painting/canvas
     :handler canvas/create-canvas}))
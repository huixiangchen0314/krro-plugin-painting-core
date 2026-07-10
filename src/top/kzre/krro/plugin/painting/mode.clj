(ns top.kzre.krro.plugin.painting.mode
  "绘画模式定义，使用新的 define-major-mode 宏。"
  (:require
   [top.kzre.krro.core.core :as core]
   [top.kzre.krro.core.frame :as frame]
   [top.kzre.krro.core.hook :as hook]
   [top.kzre.krro.core.plugin :as plugin]
   [top.kzre.krro.plugin.painting.canvas.core :as canvas]
   [top.kzre.krro.plugin.painting.canvas.state :as state]
   [top.kzre.krro.plugin.painting.spec :as spec]
   [top.kzre.krro.plugin.painting.ui.layer-browser :as lb])
  (:import
   (java.util UUID)))

(defn- watch-canvas-id [f]
  (let [watch-key ::canvas-id-watch]
    (add-watch (frame/params-atom f) watch-key
               (fn [_ _ old-params new-params]
                 (when (not= (get old-params spec/canvas-id-key)
                             (get new-params spec/canvas-id-key))
                   ;; 重绘整个布局，使新 props 生效
                   (core/rerender! f))))
    watch-key))


(defn- unwatch-canvas-id [f watch-key]
  (remove-watch (frame/params-atom f) watch-key))




(defn layout-fn [f]
  (let [canvas-id (frame/ensure-param! f spec/canvas-id-key #(keyword (str (UUID/randomUUID))))
        _rt       (state/ensure-runtime! canvas-id 800 600)     ;; 确保运行时一定有效.
        ]
    [:block {:key :root                                    ;; 标注key 防止重建.
             :direction :horizontal}
     [:krro.painting/canvas {:key canvas-id                ;; 确保画布更新时候，重建ui和状态.
                             :krro.painting/canvas-id canvas-id}]
     (lb/layer-panel-vnode canvas-id f)]))

(defn register!
  "注册画布标签到 JavaFX 标签系统。"
  []
  (core/defmajor :krro.painting/painting "Painting Mode."
                 :layout layout-fn)
  (hook/add-hook! :krro.painting/painting-mode-enter-hook
                  (fn [f]
                    (frame/set-param! f ::canvas-id-watch (watch-canvas-id f))))
  (hook/add-hook! :krro.painting/painting-mode-exit-hook
                  (fn [f]
                    (when-let [watch-key (frame/param f ::canvas-id-watch)]
                      (unwatch-canvas-id f watch-key)
                      (frame/remove-param! f ::canvas-id-watch))))
  (plugin/register-plugin!
    {:id    :krro.painting/canvas-tag
     :type    :krro.plugin/javafx-tag
     :tag     :krro.painting/canvas
     :handler canvas/create-canvas}))
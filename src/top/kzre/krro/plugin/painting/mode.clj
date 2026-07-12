(ns top.kzre.krro.plugin.painting.mode
  "绘画模式定义，使用新的 define-major-mode 宏。"
  (:require
    [top.kzre.krro.canvas.core.canvas.protocol :as cp]
    [top.kzre.krro.canvas.core.layer.core :as layer]
    [top.kzre.krro.core.core :as core]
    [top.kzre.krro.core.frame :as frame]
    [top.kzre.krro.core.hook :as hook]
    [top.kzre.krro.core.plugin :as plugin]
    [top.kzre.krro.plugin.painting.canvas.core :as canvas]
    [top.kzre.krro.plugin.painting.canvas.project :as proj]
    [top.kzre.krro.plugin.painting.canvas.state :as state]
    [top.kzre.krro.plugin.painting.spec :as spec]
    [top.kzre.krro.plugin.painting.ui.layer-browser :as lb])
  (:import
    (java.util UUID)
    (javafx.application Platform)
    (top.kzre.krro.canvas.core Arrays)))

(defn- watch-canvas-id [f]
  (let [watch-key ::canvas-id-watch]
    (add-watch (frame/params-atom f) watch-key
               (fn [_ _ old-params new-params]
                 (when (not= (get old-params spec/canvas-id-key)
                             (get new-params spec/canvas-id-key))
                   ;; 重绘整个布局，使新 props 生效
                   (core/rerender! f))))
    watch-key))

(defn- watch-layer-changed-changed!
  "- 当图层更新时候，刷新UI布局."
  [f]
  (let [cb (fn [canvas-id]
             (when (= (frame/param f spec/canvas-id-key) canvas-id)
               (core/rerender! f)))]
    (hook/add-hook! spec/layer-changed-hook-key cb)
    cb))

(defn- unwatch-canvas-id [f watch-key]
  (remove-watch (frame/params-atom f) watch-key))

(defn- watch-selected-layer-changed!
  "- 当选中图层更新时候，刷新预览状态."
  []
  (let [cb (fn [canvas-id layer-id]
             (when-let [cd (proj/canvas-data! canvas-id)]
               (when-let [l (layer/find-layer layer-id (:layers cd))]
                 (let [data (cp/data (:canvas l))
                       rt (state/canvas-runtime canvas-id)
                       buf (state/layer-buffer rt)]
                   (Arrays/copy data buf)))))]
    (hook/add-hook! spec/selected-layer-changed-hook-key cb)
    cb))

(defn- unwatch-selected-layer-changed! [cb]
  (hook/remove-hook! spec/selected-layer-changed-hook-key cb))


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
                    (frame/set-param! f ::canvas-id-watch (watch-canvas-id f))
                    (frame/set-param! f ::layer-changed-watch (watch-layer-changed-changed! f))))
  (hook/add-hook! :krro.painting/painting-mode-exit-hook
                  (fn [f]
                    (when-let [watch-key (frame/param f ::canvas-id-watch)]
                      (unwatch-canvas-id f watch-key)
                      (frame/remove-param! f ::canvas-id-watch))
                    (when-let [watch-key (frame/param f ::canvas-id-watch)]
                      (unwatch-selected-layer-changed! watch-key)
                      (frame/remove-param! f ::canvas-id-watch))))
  (watch-selected-layer-changed!)

  (plugin/register-plugin!
    {:id    :krro.painting/canvas-tag
     :type    :krro.plugin/javafx-tag
     :tag     :krro.painting/canvas
     :handler canvas/create-canvas})
  ;(plugin/register-plugin!
  ;  {:id    :krro.painting/layer-list-tag
  ;   :type  :krro.plugin/javafx-tag
  ;   :tag   :krro.painting/layer-list
  ;   :handler layer-list/create-layer-list})
  )
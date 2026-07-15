(ns top.kzre.krro.plugin.painting.mode
  "绘画模式定义，使用新的 define-major-mode 宏。"
  (:require
    [top.kzre.krro.core.core :as core]
    [top.kzre.krro.core.frame :as frame]
    [top.kzre.krro.core.hook :as hook]
    [top.kzre.krro.core.plugin :as plugin]
    [top.kzre.krro.plugin.painting.canvas.core :as canvas]
    [top.kzre.krro.plugin.painting.canvas.state :as state]
    [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
    [top.kzre.krro.plugin.painting.spec :as spec]
    [top.kzre.krro.plugin.painting.ui.tool-bar :as tb]
    [top.kzre.krro.plugin.painting.ui.layer-browser :as lb])
  (:import
    (java.util UUID)))
;; TODO 拆分成core层 和 editor 两个模块，前者定义核心数据和krro.core集成，后者集成javafx-renderer做ui.
;; krro-plugin-painting-core
;; krro-plugin-painting-editor-core 核心UI 描述，使用平台无关的描述spec,使用一些约定的tag，由具体平台提供.
;; krro-plugin-painting-editor-javafx
;; krro-plugin-painting-editor 编辑器核心集成.
;; krro-plugin-painting- GPU 加速实现 lwjgl

(defn maybe-refresh-frame-fn!
  [canvas-id f]
  (when (= (frame/param f spec/canvas-id-key) canvas-id)
    (core/rerender! f)))

(defn create-maybe-refresh-frame-fn!
  [f]
  (fn [canvas-id]
    (pc/canvas-data! canvas-id)
    (maybe-refresh-frame-fn! canvas-id f)))

(defn- watch-canvas-id [f]
  (let [watch-key ::canvas-id-watch]
    (add-watch (frame/params-atom f) watch-key
               (fn [_ _ old-params new-params]
                 (when (not= (get old-params spec/canvas-id-key)
                             (get new-params spec/canvas-id-key))
                   (core/rerender! f))))
    watch-key))

(defn- watch-layer-changed-changed!
  "- 当图层更新时候，刷新UI布局."
  [f]
  (let [cb (create-maybe-refresh-frame-fn! f)]
    (hook/add-hook! spec/layer-changed-hook-key cb)
    cb))

(defn- unwatch-canvas-id [f watch-key]
  (remove-watch (frame/params-atom f) watch-key))


(defn- unwatch-selected-layer-changed! [cb]
  (hook/remove-hook! spec/selected-layer-changed-hook-key cb))

(defn- watch-selected-layer-changed-per-frame!
  "- 当选中图层更新时候，刷新预览状态."
  [f]
  (let [cb (fn [canvas-id layer-id]
             (maybe-refresh-frame-fn! canvas-id f))]
    (hook/add-hook! spec/selected-layer-changed-hook-key cb)
    cb))

(defn unwatch-selected-layer-changed-per-frame! [cb]
  (hook/remove-hook! spec/selected-layer-changed-hook-key cb))

(defn layout-fn [f]
  (let [canvas-id (frame/ensure-param! f spec/canvas-id-key #(keyword (str (UUID/randomUUID))))
        _rt       (state/ensure-runtime! canvas-id 800 600)]
    [:block {:key :root :direction :vertical}
     ;; 顶部工具选择栏
     (tb/tool-bar-vnode canvas-id f)
     [:block {:direction :horizontal}
      ;; 左侧画布
      [:krro.painting/canvas {:key canvas-id :krro.painting/canvas-id canvas-id}]
      ;; 右侧图层面板
      (lb/layer-panel-vnode canvas-id f)]]))

(defn register!
  "注册画布标签到 JavaFX 标签系统。"
  []
  (core/defmajor :krro.painting/painting "Painting Mode."
                 :layout layout-fn)

  (hook/add-hook! :krro.painting/painting-mode-enter-hook
                  (fn [f]
                    (frame/set-param! f ::canvas-id-watch (watch-canvas-id f))
                    (frame/set-param! f ::layer-changed-watch (watch-layer-changed-changed! f))
                    (frame/set-param! f ::layer-changed-per-frame-watch (watch-selected-layer-changed-per-frame! f))))
  (hook/add-hook! :krro.painting/painting-mode-exit-hook
                  (fn [f]
                    (when-let [watch-key (frame/param f ::canvas-id-watch)]
                      (unwatch-canvas-id f watch-key)
                      (frame/remove-param! f ::canvas-id-watch))
                    (when-let [cb (frame/param f ::layer-changed-watch)]
                      (unwatch-selected-layer-changed! cb)
                      (frame/remove-param! f ::layer-changed-watch))
                    (when-let [cb (frame/param f ::layer-changed-per-frame-watch)]
                      (unwatch-selected-layer-changed-per-frame! cb)
                      (frame/remove-param! f ::layer-changed-per-frame-watch))))


  (plugin/register-plugin!
    {:id    :krro.painting/canvas-tag
     :type    :krro.plugin/javafx-tag
     :tag     :krro.painting/canvas
     :handler canvas/create-canvas})
  )
(ns top.kzre.krro.plugin.painting.canvas.core
  (:require
    [top.kzre.krro.core.frame :as frame]
    [top.kzre.krro.plugin.painting.canvas.backup :as backup]
    [top.kzre.krro.plugin.painting.canvas.input :as input]
    [top.kzre.krro.plugin.painting.canvas.layer :as layer]
    [top.kzre.krro.plugin.painting.canvas.layer-undo :as layer-undo]
    [top.kzre.krro.plugin.painting.canvas.loop :as loop]
    [top.kzre.krro.plugin.painting.canvas.state :as state]
    [top.kzre.krro.plugin.painting.canvas.upload :as upload]
    [top.kzre.krro.plugin.painting.canvas.viewport :as vp]
    [top.kzre.krro.plugin.painting.platform.javafx.input :as jfx-input]
    [top.kzre.krro.plugin.painting.project.canvas :as pc]
    [top.kzre.krro.plugin.painting.spec :as spec]
    [top.kzre.krro.plugin.painting.tool.brush :as brush-tool]
    [top.kzre.krro.plugin.painting.tool.protocol :as tp]
    [top.kzre.krro.ui.javafx.core :refer [make-component]])
  (:import
   (javafx.scene.canvas Canvas)))

(defn- start-canvas-session [^Canvas canvas canvas-id f]
  (let [runtime (state/canvas-runtime canvas-id)
        [w h]   (pc/canvas-size canvas-id)
        upload-fn (upload/make-uploader canvas)
        _ (state/set-current-tool! canvas-id (brush-tool/make-brush))
        ;; 渲染函数：每帧由动画循环调用
        render-fn (fn []
                    (when-let [current-tool (state/current-tool canvas-id)]
                      (when-let [layer (state/selected-layer! canvas-id)]
                        (let [data (pc/canvas-data! canvas-id)
                              ctx  (tp/make-context canvas-id f data runtime)]
                          (when-let [new-layer (tp/preview! current-tool layer ctx)]
                            (layer/replace-layer! canvas-id new-layer))))))

        ;; 输入回调：根据 apply! 返回的动作指令调度
        callback (fn [ev]
                   (when-let [current-tool (state/current-tool canvas-id)]
                     (when-let [layer (state/selected-layer! canvas-id)]
                       (let [data (pc/canvas-data! canvas-id)
                             ctx  (tp/make-context canvas-id f data runtime)
                             action (tp/apply! current-tool layer ev ctx)]
                         (case action
                           :start    (do (backup/backup-layer! layer runtime)
                                         (loop/start-loop! canvas-id render-fn))
                           :continue nil
                           :commit   (do (loop/stop-loop! canvas-id)
                                         (when-let [new-layer (tp/commit! current-tool layer ctx)]
                                           (layer-undo/replace-layer-undo! canvas-id new-layer)))
                           :idle     nil)))))

        ;; 创建输入源
        mouse-input (jfx-input/make-mouse-input canvas callback)]

    ;; 启动输入源
    (input/start! mouse-input)

    (.setWidth canvas (double w))
    (.setHeight canvas (double h))
    (layer/auto-select-layer! canvas-id)

    ;; 首次上传画布
    (when-let [preview (state/preview-buffer runtime)]
      (upload-fn preview w h (vp/get-viewport f)))

    (frame/set-param! f spec/update-fn-key upload-fn)

    ;; 返回清理函数
    (fn []
      (input/stop! mouse-input)
      (loop/stop-loop! canvas-id)
      (frame/remove-param! f spec/update-fn-key))))

(def create-canvas
  (make-component [:krro.painting/canvas-id]
                  (fn [] (doto (Canvas.) (.setId "painting-canvas")))
                  (fn [^Canvas canvas old-props new-props f]
                    (let [canvas-id (:krro.painting/canvas-id new-props)]
                      (if (nil? old-props)
                        ;; 首次挂载：创建完整会话
                        (start-canvas-session canvas canvas-id f)
                        ;; 更新（canvas-id 未变）：仅重新渲染当前运行时内容
                        (when-let [runtime (state/canvas-runtime canvas-id)]
                          (let [[w h]   (pc/canvas-size canvas-id)
                                preview  (state/preview-buffer runtime)
                                viewport (vp/get-viewport f)
                                upload-fn (frame/param f spec/update-fn-key)]
                            (when upload-fn
                              (upload-fn preview w h viewport)))))))))
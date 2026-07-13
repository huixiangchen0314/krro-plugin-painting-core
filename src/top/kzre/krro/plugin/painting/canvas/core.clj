(ns top.kzre.krro.plugin.painting.canvas.core
  (:require
    [top.kzre.krro.core.frame :as frame]
    [top.kzre.krro.plugin.painting.canvas.input :as input]
    [top.kzre.krro.plugin.painting.canvas.layer :as layer]
    [top.kzre.krro.plugin.painting.canvas.loop :as loop]
    [top.kzre.krro.plugin.painting.canvas.state :as state]
    [top.kzre.krro.plugin.painting.canvas.upload :as upload]
    [top.kzre.krro.plugin.painting.project.canvas :as pc]
    [top.kzre.krro.plugin.painting.spec :as spec]
    [top.kzre.krro.ui.javafx.core :refer [make-component]])
  (:import
   (javafx.scene.canvas Canvas)))


(defn- start-canvas-session [^Canvas canvas canvas-id f]
  (let [runtime (state/canvas-runtime canvas-id)
        [w h]   (pc/canvas-size canvas-id)
        upload-fn (upload/make-uploader canvas)             ;; TODO无限画布.
        loop-ctrl (loop/make-loop canvas-id runtime w h  f)
        timer    (:timer loop-ctrl)
        commit!  (:commit loop-ctrl)

        input-src (input/make-mouse-input)
        stop-input ((:start! input-src) canvas runtime
                    {:on-stroke-start #(.start timer)
                     :on-stroke-end   #(do (commit!) (.stop timer))})]
    (.setWidth canvas (double w))
    (.setHeight canvas (double h))
    (layer/auto-select-layer! canvas-id)
    ;; 首次渲染：上传当前画布的初始内容
    (when-let [preview (state/preview-buffer runtime)]
      (upload-fn preview w h))
    ;; 保存状态.
    (frame/set-param! f spec/update-fn-key upload-fn)   ;; 保存 upload-fn 到 Frame
    ;; 返回清理函数
    (fn []
      (stop-input)
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
                                upload-fn (frame/param f ::upload-fn)]
                            (when upload-fn
                              (upload-fn preview w h)))))))))
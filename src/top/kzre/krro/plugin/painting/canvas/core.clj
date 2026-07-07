(ns top.kzre.krro.plugin.painting.canvas.core
  (:require
    [top.kzre.krro.plugin.painting.canvas.project :as canvas-proj]
    [top.kzre.krro.plugin.painting.canvas.state :as state]
    [top.kzre.krro.plugin.painting.canvas.upload :as upload]
    [top.kzre.krro.plugin.painting.canvas.loop :as loop]
    [top.kzre.krro.plugin.painting.canvas.brush :as brush]
    [top.kzre.krro.plugin.painting.canvas.input :as input])
  (:import [javafx.scene.canvas Canvas]))

(defn create-canvas [props]
  (let [canvas-id   (get props :krro.painting/canvas-id (str (java.util.UUID/randomUUID)))
        default-w   (int (or (:krro.painting/canvas-width props) 800))
        default-h   (int (or (:krro.painting/canvas-height props) 600))
        canvas-data (canvas-proj/polyfill-canvas-data canvas-id default-w default-h)
        runtime     (state/create canvas-data)
        _           (state/set-current-brush! runtime brush/default-brush)
        w           (long (.width canvas-data))
        h           (long (.height canvas-data))
        fx-canvas   (Canvas. w h)
        upload-fn   (upload/make-uploader fx-canvas)
        loop-ctrl   (loop/make-loop runtime upload-fn)
        timer       (:timer loop-ctrl)
        commit!     (:commit loop-ctrl)
        input-src   (input/make-mouse-input)]
    ;; 启动输入源，注入 timer 控制
    ((:start! input-src) fx-canvas runtime
     {:on-stroke-start #(.start timer)
      :on-stroke-end   #(do (commit!) (.stop timer))})
    fx-canvas))
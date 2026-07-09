(ns top.kzre.krro.plugin.painting.canvas.core
  (:require
   [top.kzre.krro.core.frame :as frame]
   [top.kzre.krro.plugin.painting.canvas.input :as input]
   [top.kzre.krro.plugin.painting.canvas.loop :as loop]
   [top.kzre.krro.plugin.painting.canvas.project :as canvas-proj]
   [top.kzre.krro.plugin.painting.canvas.state :as state]
   [top.kzre.krro.plugin.painting.canvas.upload :as upload]
   [top.kzre.krro.plugin.painting.canvas.undo :as painting-undo]
   [top.kzre.krro.plugin.painting.spec :as spec])
  (:import
   (java.util UUID)
   [javafx.scene.canvas Canvas]))

(defn create-canvas [props f]
  (let [canvas-id (or (when f (frame/param f spec/canvas-id-key))
                      (str (UUID/randomUUID)))
        canvas-width (int (or (when f (frame/param f spec/canvas-width-key))
                              (:krro.painting/canvas-width props)
                              800))
        canvas-height (int (or (when f (frame/param f spec/canvas-height-key))
                               (:krro.painting/canvas-height props)
                               600))
        canvas-data (canvas-proj/polyfill-canvas-data! canvas-id canvas-width canvas-height)
        runtime     (state/create canvas-data)
        w           (long (.width canvas-data))
        h           (long (.height canvas-data))
        fx-canvas   (Canvas. w h)
        upload-fn   (upload/make-uploader fx-canvas)
        cleanup-undo (painting-undo/init-undo-hooks! upload-fn)
        loop-ctrl   (loop/make-loop runtime upload-fn)
        timer       (:timer loop-ctrl)
        commit!     (:commit loop-ctrl)
        input-src   (input/make-mouse-input)
        stop-input  ((:start! input-src) fx-canvas runtime
                     {:on-stroke-start #(.start timer)
                      :on-stroke-end   #(do (commit!) (.stop timer))})]
    (frame/set-param! f spec/canvas-runtime-key runtime)
    {:node      fx-canvas
     :on-unmount   (fn []
                  (cleanup-undo)
                  (stop-input))}))
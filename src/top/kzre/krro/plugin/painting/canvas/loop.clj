(ns top.kzre.krro.plugin.painting.canvas.loop
  (:require
    [taoensso.timbre :as log]
    [top.kzre.krro.plugin.painting.canvas.layer :as layer]
    [top.kzre.krro.plugin.painting.canvas.state :as state]
    [top.kzre.krro.plugin.painting.stroke.raster-brush :as raster])
  (:import
    [javafx.animation AnimationTimer]))


(defn preview-stroke-events! [canvas-id]
  (case (state/selected-layer-type canvas-id)
    :raster (raster/preview! canvas-id)
    nil))

(defn commit-stroke! [canvas-id]
  (case (state/selected-layer-type canvas-id)
    :raster (raster/commit! canvas-id)
    nil))

(defn make-loop [canvas-id]
  (let [timer (proxy [AnimationTimer] []
                (handle [_]
                  (try
                    (preview-stroke-events! canvas-id)
                    (layer/refresh-canvas-frames! canvas-id)
                    (catch Exception e
                      (log/error e (str "Render loop error: " (.getMessage e)))))))

        commit-fn #(do (commit-stroke! canvas-id)
                       (layer/refresh-canvas-frames! canvas-id))]
    {:timer timer :commit commit-fn}))
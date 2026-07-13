(ns top.kzre.krro.plugin.painting.canvas.upload
  "将渲染结果上传到 JavaFX Canvas，支持视口变换。"
  (:require [top.kzre.krro.plugin.painting.canvas.viewport])
  (:import (javafx.application Platform)
           (javafx.scene.canvas Canvas)
           (javafx.scene.image PixelWriter)
           (top.kzre.krro.plugin.painting.canvas.viewport ViewPort)
           (top.kzre.krro.plugin.painting.canvas ViewportUploader)))

(defn make-uploader
  [^Canvas fx-canvas]
  (let [^PixelWriter pixel-writer (.getPixelWriter (.getGraphicsContext2D fx-canvas))]
    (fn [^floats data w h ^ViewPort viewport]
      (let [canvas-w (int (.getWidth fx-canvas))
            canvas-h (int (.getHeight fx-canvas))]
        (Platform/runLater
          (fn []
            (ViewportUploader/upload data w h
                                     (:offset-x viewport) (:offset-y viewport) (:zoom viewport)
                                     pixel-writer canvas-w canvas-h)
            nil))))))
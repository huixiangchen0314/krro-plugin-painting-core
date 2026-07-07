(ns top.kzre.krro.plugin.painting.canvas.upload
  "将渲染结果上传到 JavaFX Canvas。
   负责像素格式转换（预乘 ARGB）并在 JavaFX 线程上安全执行。"
  (:import (javafx.application Platform)
           (javafx.scene.canvas Canvas)
           (javafx.scene.image PixelFormat)))

(defn- convert-to-int-array
  "将 float RGBA 平面数组转换为预乘 int ARGB 数组。"
  [^floats src w h]
  (let [len (* w h)
        dst (int-array len)]
    (dotimes [y h]
      (dotimes [x w]
        (let [idx   (* 4 (+ x (* y w)))
              r     (aget src idx)
              g     (aget src (inc idx))
              b     (aget src (+ idx 2))
              a     (aget src (+ idx 3))
              a-int (max 0 (min 255 (int (* a 255))))
              pre-r (max 0 (min 255 (int (* r a 255))))
              pre-g (max 0 (min 255 (int (* g a 255))))
              pre-b (max 0 (min 255 (int (* b a 255))))
              pixel (unchecked-int (bit-or (bit-shift-left a-int 24)
                                           (bit-shift-left pre-r 16)
                                           (bit-shift-left pre-g 8)
                                           pre-b))]
          (aset dst (+ x (* y w)) pixel))))
    dst))

(defn make-uploader
  "创建一个上传函数，绑定到指定的 JavaFX Canvas。
   返回 (fn [data w h])，调用后会将 float-array 数据渲染到画布上。"
  [^Canvas fx-canvas]
  (let [gc (.getGraphicsContext2D fx-canvas)
        pixel-writer (.getPixelWriter gc)]
    (fn [^floats data w h]
      (let [pixels (convert-to-int-array data w h)]
        (Platform/runLater
          (fn []
            (.setPixels pixel-writer 0 0 w h
                        (PixelFormat/getIntArgbPreInstance)
                        pixels 0 w)
            nil))))))


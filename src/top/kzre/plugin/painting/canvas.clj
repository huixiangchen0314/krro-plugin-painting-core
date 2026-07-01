(ns top.kzre.plugin.painting.canvas
  "基于 LWJGL/OpenGL 的离屏画布，使用兼容模式支持固定管线。"
  (:import (java.nio ByteBuffer)
           (java.util.concurrent Executors)
           (javafx.application Platform)
           (javafx.event EventHandler)
           (javafx.scene.canvas Canvas)
           (javafx.scene.image PixelBuffer PixelFormat WritableImage)
           (javafx.scene.paint Color)
           (javafx.util Callback)
           (org.lwjgl.glfw GLFW GLFWErrorCallback)
           (org.lwjgl.opengl GL GL32)))

;; ── OpenGL 初始化 ──────────────────────────────────────
(defn- init-gl []
  (GLFWErrorCallback/createPrint)
  (when-not (GLFW/glfwInit)
    (throw (Exception. "GLFW init failed")))
  (GLFW/glfwDefaultWindowHints)
  (GLFW/glfwWindowHint GLFW/GLFW_VISIBLE GLFW/GLFW_FALSE)
  (GLFW/glfwWindowHint GLFW/GLFW_CONTEXT_VERSION_MAJOR 3)
  (GLFW/glfwWindowHint GLFW/GLFW_CONTEXT_VERSION_MINOR 3)
  ;; 使用兼容模式，允许固定管线函数
  (GLFW/glfwWindowHint GLFW/GLFW_OPENGL_PROFILE GLFW/GLFW_OPENGL_COMPAT_PROFILE))

;; ── 创建 FBO ──────────────────────────────────────────
(defn- create-fbo [width height]
  (let [fbo    (GL32/glGenFramebuffers)
        tex-id (GL32/glGenTextures)]
    (GL32/glBindFramebuffer GL32/GL_FRAMEBUFFER fbo)
    (GL32/glBindTexture GL32/GL_TEXTURE_2D tex-id)
    (GL32/glTexImage2D GL32/GL_TEXTURE_2D 0 GL32/GL_RGBA8 width height 0
                       GL32/GL_RGBA GL32/GL_UNSIGNED_BYTE 0)
    (GL32/glFramebufferTexture2D GL32/GL_FRAMEBUFFER GL32/GL_COLOR_ATTACHMENT0
                                 GL32/GL_TEXTURE_2D tex-id 0)
    (when (not= (GL32/glCheckFramebufferStatus GL32/GL_FRAMEBUFFER)
                GL32/GL_FRAMEBUFFER_COMPLETE)
      (println "Warning: FBO incomplete"))
    fbo))

;; ── 渲染一帧 ──────────────────────────────────────────
(defn- render-frame [width height points]
  (GL32/glClearColor 1.0 1.0 1.0 1.0)
  (GL32/glClear (bit-or GL32/GL_COLOR_BUFFER_BIT GL32/GL_DEPTH_BUFFER_BIT))
  (doseq [[x y] @points]
    (let [nx (float (- (/ (* 2 x) width) 1.0))
          ny (float (- 1.0 (/ (* 2 y) height)))]
      (GL32/glColor3f 1.0 0.0 0.0)
      (GL32/glPointSize 20.0)
      (GL32/glBegin GL32/GL_POINTS)
      (GL32/glVertex2f nx ny)
      (GL32/glEnd))))

;; ── 更新 JavaFX Canvas ────────────────────────────────
(defn- update-canvas [canvas byte-buffer pixel-buffer writable-img]
  (let [width  (int (.getWidth canvas))
        height (int (.getHeight canvas))]
    (GL32/glReadPixels 0 0 width height GL32/GL_BGRA GL32/GL_UNSIGNED_BYTE byte-buffer)
    (Platform/runLater
      (fn []
        (.updateBuffer pixel-buffer
                       (reify Callback
                         (call [_ _] nil)))
        (let [gc (.getGraphicsContext2D canvas)]
          (.clearRect gc 0 0 (double width) (double height))
          (.drawImage gc writable-img 0 0 (double width) (double height)))))))

;; ── 渲染循环 ──────────────────────────────────────────
(defn- render-loop [window canvas byte-buffer pixel-buffer writable-img points]
  (while (not (GLFW/glfwWindowShouldClose window))
    (render-frame (.getWidth canvas) (.getHeight canvas) points)
    (update-canvas canvas byte-buffer pixel-buffer writable-img)
    (GLFW/glfwPollEvents)
    (Thread/sleep 16)))

;; ── 纯 JavaFX 回退 ────────────────────────────────────
(defn- fallback-to-javafx [canvas]
  (Platform/runLater
    (fn []
      (let [gc (.getGraphicsContext2D canvas)]
        (.setFill gc Color/WHITE)
        (.fillRect gc 0 0 (.getWidth canvas) (.getHeight canvas))
        (.setOnMouseClicked canvas
                            (reify EventHandler
                              (handle [_ event]
                                (let [x (.getX event) y (.getY event)]
                                  (.setFill gc Color/RED)
                                  (.fillOval gc (- x 10) (- y 10) 20 20)))))))))

;; ── 公开构造器 ────────────────────────────────────────
(defn create-canvas [props]
  (let [width  (int (or (:width props) 800))
        height (int (or (:height props) 600))
        canvas (Canvas. (double width) (double height))
        points (atom [])
        pixel-format (PixelFormat/getByteBgraPreInstance)
        byte-buffer  (ByteBuffer/allocateDirect (* width height 4))
        pixel-buffer (PixelBuffer. width height byte-buffer pixel-format)
        writable-img (WritableImage. pixel-buffer)]

    (.setOnMouseClicked canvas
                        (reify EventHandler
                          (handle [_ event]
                            (swap! points conj [(.getX event) (.getY event)]))))

    (let [executor (Executors/newSingleThreadExecutor)]
      (.execute executor
                (fn []
                  (try
                    (init-gl)
                    (let [window (GLFW/glfwCreateWindow width height "" 0 0)]
                      (when (= window 0)
                        (throw (Exception. "GLFW window creation failed")))
                      (GLFW/glfwMakeContextCurrent window)
                      (GL/createCapabilities)
                      (create-fbo width height)
                      (render-loop window canvas byte-buffer pixel-buffer writable-img points)
                      (GLFW/glfwDestroyWindow window)
                      (GLFW/glfwTerminate))
                    (catch Exception e
                      (println "OpenGL rendering failed, falling back to Canvas:" (.getMessage e))
                      (fallback-to-javafx canvas))))))
  canvas))
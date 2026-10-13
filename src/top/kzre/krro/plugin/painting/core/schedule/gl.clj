(ns top.kzre.krro.plugin.painting.core.schedule.gl
  (:require
    [top.kzre.krro.plugin.painting.core.project.canvas :as pc])
  (:import
    (top.kzre.krro.canvas.gl GLExecutors)
    (top.kzre.krro.canvas.gl.composite GLCompositeContextBuilder)
    (top.kzre.krro.canvas.gl.resource NoGLTexturePool PixelFormat)
    (top.kzre.krro.canvas.gl.tile GLRgba8AtlasFactory GLTiledTextureLayout)))

(defn default-composite-context-builder
  []
  (let [tile-size pc/global-tile-size
        executor  (GLExecutors/offscreen)
        layout    (GLTiledTextureLayout/square 8 (int tile-size) 4)
        tex-pool  (NoGLTexturePool. executor)
        factory   (GLRgba8AtlasFactory. layout tex-pool)]
    (-> (GLCompositeContextBuilder.)
        (.glExecutor executor)
        (.tileSize (int tile-size))
        (.atlasCapacity 3)
        (.atlasFactory factory)
        (.viewFboPoolCapacity 4)
        (.pixelFormat PixelFormat/RGBA8))))

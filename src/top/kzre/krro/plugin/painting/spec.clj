(ns top.kzre.krro.plugin.painting.spec
  "绘画插件相关的 Frame 参数键定义。")


(def canvas-scheme-key :krro.painting/canvas)
(def raster-scheme-key :krro.painting/raster)
;; Frame 参数中使用的命名空间关键字
(def canvas-id-key  ::canvas-id)


(def selected-layer-changed-hook-key ::selected-layer-changed)

;; 私有 hook
(def update-fn-key ::update-fn)

(def layer-changed-hook-key ::layer-changed-hook)
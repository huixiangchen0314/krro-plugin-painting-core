(ns top.kzre.krro.plugin.painting.spec
  "绘画插件相关的 Frame 参数键定义。")

;; Frame 参数中使用的命名空间关键字
(def canvas-id-key  ::canvas-id)
(def canvas-width-key  ::canvas-width)
(def canvas-height-key ::canvas-height)
(def canvas-runtime-key ::canvas-runtime)

(def selected-layer-id-key ::selected-layer-id)

;; 私有 hook
(def canvas-dirty-hook-key ::canvas-dirty-hook)
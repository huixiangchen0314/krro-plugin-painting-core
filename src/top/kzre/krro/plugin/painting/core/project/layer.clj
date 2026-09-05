(ns top.kzre.krro.plugin.painting.core.project.layer)

(defn opacity
  "返回图层的不透明度。"
  [layer]
  (:opacity layer 1.0))

(defn blend-mode
  "返回图层的混合模式。"
  [layer]
  (:blend-mode layer :normal))

(defn visible?
  "返回图层是否可见。"
  [layer]
  (:visible layer true))
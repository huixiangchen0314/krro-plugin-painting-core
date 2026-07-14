(ns top.kzre.krro.plugin.painting.tool.registry
  (:require
   [top.kzre.krro.plugin.painting.tool.brush :as brush-tool]
   [top.kzre.krro.plugin.painting.tool.move :as move]))
(def tools
  [{:id :brush :name "画笔" :icon "🖌"
    :make-fn #(brush-tool/make-brush)}
   {:id :move :name "移动" :icon "✥"
    :make-fn #(move/make-move-tool)}])
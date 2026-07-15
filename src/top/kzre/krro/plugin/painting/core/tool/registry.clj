(ns top.kzre.krro.plugin.painting.core.tool.registry
  (:require
   [top.kzre.krro.plugin.painting.core.tool.brush :as brush-tool]
   [top.kzre.krro.plugin.painting.core.tool.move :as move]
   [top.kzre.krro.plugin.painting.core.tool.vector-brush :as vector-brush]))
(def tools
  [{:id :brush :name "画笔" :icon "🖌"
    :make-fn #(brush-tool/make-brush)}
   {:id :move :name "移动" :icon "✥"
    :make-fn #(move/make-move-tool)}
   {:id :vector-brush :name "矢量笔" :icon "🖋"
    :make-fn #(vector-brush/make-vector-brush)}])
(ns top.kzre.krro.plugin.painting.tool.registry
  (:require
   [top.kzre.krro.plugin.painting.tool.brush :as brush-tool]
   [top.kzre.krro.plugin.painting.tool.move :as move]
   [top.kzre.krro.plugin.painting.tool.vector-brush :as vector-brush]))
(def tools
  [{:id :brush :name "画笔" :icon "🖌"
    :make-fn #(brush-tool/make-brush)}
   {:id :move :name "移动" :icon "✥"
    :make-fn #(move/make-move-tool)}
   {:id :vector-brush :name "矢量笔" :icon "🖋"
    :make-fn #(vector-brush/make-vector-brush)}])
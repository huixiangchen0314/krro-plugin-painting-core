(ns top.kzre.krro.plugin.painting.core.edit.tools)

(defn tools
  []
  [{:id :brush :name "画笔" :icon "🖌"}
   {:id :vector-brush :name "矢量笔" :icon "🖌"}
   {:id :move :name "移动" :icon "✥"}
   {:id :fill :name "填充" :icon "[]"}
   {:id :anchor :name "锚点" :icon "+"}])
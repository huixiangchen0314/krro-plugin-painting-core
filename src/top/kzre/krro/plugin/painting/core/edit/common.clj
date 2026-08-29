(ns top.kzre.krro.plugin.painting.core.edit.common)

(defn clear-tool-data [record]
  (assoc-in record [:Canvas-state :tool-data] {}))
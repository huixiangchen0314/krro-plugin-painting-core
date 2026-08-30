(ns top.kzre.krro.plugin.painting.core.edit.common
  (:require [top.kzre.krro.plugin.painting.core.edit.protocol :as p]))

(defn cleanup-tool-data! [record]
  (let [tool-data (get-in record [:canvas-state :tool-data])]
    (when (and tool-data
               (satisfies? p/IToolData tool-data))
      (p/cleanup tool-data))
    (assoc-in record [:canvas-state :tool-data] {})))

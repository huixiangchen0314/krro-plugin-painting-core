(ns top.kzre.krro.plugin.painting.core.schedule.mask
  (:require [top.kzre.krro.core.util.computing-graph :as cg]))


(defrecord MaskNode []
  cg/INode)
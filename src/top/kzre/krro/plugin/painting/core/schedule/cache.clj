(ns top.kzre.krro.plugin.painting.core.schedule.cache
  (:import (top.kzre.krro.core.util.computing_graph ComputingGraph))
  (:require
    [top.kzre.krro.core.util.computing-graph :as cg]))

(defrecord CompositeCacheNode
  []
  cg/INode)
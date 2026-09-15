(ns top.kzre.krro.plugin.painting.core.schedule.result
  (:require
    [top.kzre.krro.core.util.computing-graph :as cg]
    [top.kzre.krro.core.util.promise :as promise])
  (:import (top.kzre.krro.core.util.computing_graph INode)))

(defonce ^:private result-key* ::result)

(defn result-key [] result-key*)

(defrecord ResultNode [dep-id]
  cg/INode
  (node-id [_] (result-key))
  (dependencies [_] [dep-id])
  (compute [_ [input]]
    (promise/resolved input)))

(defn make-result-node [^INode node]
  (->ResultNode (cg/node-id node)))
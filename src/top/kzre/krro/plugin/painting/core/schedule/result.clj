(ns top.kzre.krro.plugin.painting.core.schedule.result
  (:require
    [top.kzre.krro.core.util.computing-graph :as cg]
    [top.kzre.krro.core.util.promise :as promise]
    [top.kzre.krro.plugin.painting.core.schedule.protocol :as proto]
    [top.kzre.krro.plugin.painting.core.viewport :as vp])
  (:import (top.kzre.krro.core.util.computing_graph INode)
           (top.kzre.krro.plugin.painting.core.viewport ViewPort)))

(defonce ^:private result-key* ::result)

(defn result-key [] result-key*)


(defrecord ResultState
          [cached-layer
           ;; 视口变换
           ^ViewPort viewport
           ;; 视口宽高
           viewport-w
           viewport-h])
(defn make-state
  []
  (->ResultState nil vp/default-viewport 0 0))

(defrecord ResultNode [dep-id
                       state-atom]
  proto/ICachingNode
  (set-caching! [_ b]  true)
  (migrate [_ other change])
  cg/INode
  (node-id [_] (result-key))
  (dependencies [_] [dep-id])
  (compute [_ [input]]
    ;; 更新缓存
    (promise/resolved input)))

(defn make-result-node [^INode node]
  (->ResultNode (cg/node-id node) (atom (make-state))))
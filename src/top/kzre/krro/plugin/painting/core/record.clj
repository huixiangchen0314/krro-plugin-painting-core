(ns top.kzre.krro.plugin.painting.core.record
  (:require [clojure.spec.alpha :as s]
            [top.kzre.krro.plugin.painting.core.project.canvas :as pc]))

;; 矩形选框
(s/def ::selection (s/keys :req-un [::start-x ::start-y
                                    ::end-x ::end-y]))

(s/def ::canvas-id keyword?)

(s/def ::canvas-data ::pc/canvas-data)
(s/def ::canvas-record
  (s/keys :req-un [::canvas-id                              ;; 画布id
                   ::canvas-data
                   ::canvas-state
                   ]
          :opt-un [::selection]))
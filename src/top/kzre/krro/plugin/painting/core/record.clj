(ns top.kzre.krro.plugin.painting.core.record
  (:require [clojure.spec.alpha :as s]
            [top.kzre.krro.curve.bezier2d.spec :as-alias bezier]
            [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
            [top.kzre.krro.plugin.painting.core.store :as store]))


(s/def ::cursor-position ::bezier/point)

(defn current-tool [record]
  (or (:current-tool record)
      (get-in record [:canvas-state :tool-data])))

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
          :opt-un [::selection
                   ::cursor-position]))


(defonce record store/record)
(defn cursor-position [canvas-id]
  (get (record canvas-id) :cursor-position))
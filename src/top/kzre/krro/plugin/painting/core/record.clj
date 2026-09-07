(ns top.kzre.krro.plugin.painting.core.record
  (:require [clojure.spec.alpha :as s]
            [top.kzre.krro.curve.bezier2d.spec :as-alias bezier]
            [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
            [top.kzre.krro.plugin.painting.core.store :as store]))


(s/def ::cursor-position ::bezier/point)

(defn current-tool [record]
  (or (:current-tool record)
      (get-in record [:canvas-state :tool-data])))


(defn current-layer-id
  [record]
  (get-in record [:canvas-data :current-layer-id]))

(defn pivot-center [record]
  (get-in record [:canvas-data :pivot-center] :aabb-center))

(defn snap-options [record]
  (get-in record [:canvas-data :snap-options] {}))

(defn falloff-options [record]
  (get-in record [:canvas-data :falloff-options] {}))

(defn horizontal-mirror-enabled [record]
  (get-in record [:canvas-data :horizontal-mirror-enabled] false))

(defn horizontal-mirror-center [record]
  (get-in record [:canvas-data :horizontal-mirror-center]))

(defn vertical-mirror-enabled [record]
  (get-in record [:canvas-data :vertical-mirror-enabled] false))

(defn vertical-mirror-center [record]
  (get-in record [:canvas-data :vertical-mirror-center]))

(defn tiling-enabled [record]
  (get-in record [:canvas-data :tiling-enabled] false))



(s/def ::canvas-id keyword?)

(s/def ::canvas-data ::pc/canvas-data)


(s/def ::canvas-record
  (s/keys :req-un [::canvas-id                              ;; 画布id
                   ::canvas-data
                   ::canvas-state
                   ]
          :opt-un [::cursor-position]))


(defonce record store/record)
(defn cursor-position [canvas-id]
  (get (record canvas-id) :cursor-position))
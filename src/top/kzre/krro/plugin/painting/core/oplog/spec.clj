(ns top.kzre.krro.plugin.painting.core.oplog.spec
  (:require [clojure.spec.alpha :as s]))

(s/def ::type       keyword?)
(s/def ::transient  boolean?)


(s/def ::action-base
  (s/keys :req-un [::type]
          :opt-un [::transient ]))
(s/def ::unknown-action ::action-base)


(defmulti action-type :type)

(defmethod action-type :default [_] ::unknown-action)

(s/def ::action  (s/multi-spec action-type :type))
(s/def ::actions (s/coll-of ::action :kind vector?))
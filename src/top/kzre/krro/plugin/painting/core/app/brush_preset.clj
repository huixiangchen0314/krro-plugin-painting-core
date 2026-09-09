(ns top.kzre.krro.plugin.painting.core.app.brush-preset
  "笔刷预设"
  (:require [clojure.spec.alpha :as s]
            [top.kzre.krro.brush.spec :as brush]
            [top.kzre.krro.core.rdb :refer [defschema]]))

(s/def ::id keyword?)
(s/def ::name string?)
(s/def ::tag keyword?)
(s/def ::tags (s/coll-of ::tag :kind vector?))
(s/def ::icon any?)                        ; 可为数据 URI、关键字或 nil
(s/def ::favorite? boolean?)
(s/def ::category string?)                 ; 分组类别，如 "基础", "纹理", "特效"

(s/def ::brush ::brush/brush)              ; 直接引用外部规格

(s/def ::brush-preset
  (s/keys :req-un [::id ::name ::brush]
          :opt-un [::icon ::tags ::favorite? ::category ::created-at ::updated-at]))

(defschema :brush-preset
           :primary-key :id
           :spec ::brush-preset)
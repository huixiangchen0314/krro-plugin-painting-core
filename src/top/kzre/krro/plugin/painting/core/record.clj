(ns top.kzre.krro.plugin.painting.core.record
  (:require [clojure.spec.alpha :as s]
            [top.kzre.krro.curve.bezier2d.spec :as-alias bezier]
            [top.kzre.krro.plugin.painting.core.edit.falloff :as-alias falloff]
            [top.kzre.krro.plugin.painting.core.edit.snap :as-alias snap]
            [top.kzre.krro.plugin.painting.core.edit.spec :as-alias edit]))


;; 矩形选框
(s/def ::selection (s/keys :req-un [::start-x ::start-y
                                    ::end-x ::end-y]))

(s/def ::canvas-id keyword?)


;; 带外数据不进行管理
(s/def ::canvas-state any?)


(s/def ::canvas-record
  (s/keys :req-un [::canvas-id                              ;; 画布id
                   ::canvas-data
                   ::canvas-state
                   ]
          :opt-un [::selection]))
(ns top.kzre.krro.plugin.painting.core.app.texture
  "纹理资源"
  (:require [clojure.spec.alpha :as s]
            [top.kzre.krro.core.rdb :refer [defschema]]))

;; ── 纹理 ──────────────────────────────────
(s/def ::id keyword?)
(s/def ::name string?)
(s/def ::category keyword?)           ; 如 :brush-tip, :pattern, :paper 等
(s/def ::tags (s/coll-of keyword? :kind vector?))
(s/def ::image string?)          ; 图像数据：路径、URI 或 base64


(s/def ::texture
  (s/keys :req-un [::id ::name  ::image]
          :opt-un [::category ::tags ::thumbnail]))

(defschema :texture
           :primary-key :id
           :spec ::texture)
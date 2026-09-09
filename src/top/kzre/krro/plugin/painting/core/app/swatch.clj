(ns top.kzre.krro.plugin.painting.core.app.swatch
  "色板 1-n 色块组.
  色块组 1-n 色块"
  (:require
    [top.kzre.krro.core.rdb :as rdb]
    [clojure.spec.alpha :as s]))

;; ── 颜色值 ──────────────────────────────────
(s/def ::id keyword?)
(s/def ::rgb (s/tuple float? float? float? (s/? float?))) ; [r g b] 或 [r g b a]
(s/def ::name string?)   ; 专色名称或自定义颜色名称

(s/def ::color (s/or :simple ::rgb
                     :named (s/keys :req-un [::rgb]
                                    :opt-un [::name])))

;; ── 色组 ──────────────────────────────────
(s/def ::colors (s/map-of int? ::color))      ; idx -> color，无 nil
(s/def ::columns int?)   ; 每行显示色块数,也就是列数
(s/def ::size int?)
(s/def ::group-name string?)

(s/def ::color-group (s/keys :req-un [::colors ::size ::columns ]
                             :opt-un [::group-name]))

(s/def ::color-groups (s/coll-of ::color-group :kind vector?))
;; ── 色板 ──────────────────────────────────
(s/def ::icon any?)

(s/def ::swatch
  (s/keys :req-un [::id
                   ::name
                   ::color-groups]
          :opt-un [::icon]))

;; ── RDB Schema ──────────────────────────────────
(rdb/defschema :swatch
               :primary-key :id
               :spec ::swatch)


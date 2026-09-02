(ns top.kzre.krro.plugin.painting.core.record
  (:require [clojure.spec.alpha :as s]
            [top.kzre.krro.plugin.painting.core.edit.falloff :as-alias falloff]
            [top.kzre.krro.plugin.painting.core.edit.snap :as-alias snap]
            [top.kzre.krro.plugin.painting.core.edit.spec :as-alias edit]))

(s/def ::canvas-id keyword?)

;; 带外数据不进行管理
(s/def ::canvas-data any?)
;; 带外数据不进行管理
(s/def ::canvas-state any?)

;;变换轴心点
(s/def ::pivot-center ::edit/pivot-center)
(s/def ::snap-options ::snap/snap-options)
(s/def ::falloff-options ::falloff/falloff-options)
(s/def ::canvas-record
  (s/keys :req-un [::canvas-id                              ;; 画布id
                   ::pivot-center                           ;; 变换轴心点
                   ::snap-options                           ;; 吸附选项
                   ::falloff-options                        ;; 衰减编辑选项
                   ::canvas-data
                   ::canvas-state
                   ]))
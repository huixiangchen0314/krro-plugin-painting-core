(ns top.kzre.krro.plugin.painting.core.session.session
  (:require [clojure.spec.alpha :as s]
            [top.kzre.krro.plugin.painting.core.edit.falloff :as falloff]
            [top.kzre.krro.plugin.painting.core.edit.snap :as snap]
            [top.kzre.krro.plugin.painting.core.edit.spec :as edit]
            [top.kzre.krro.plugin.painting.core.session.store :as session.store]))


;; =============================== 应用全局数据(从 app.clj/session.clj 合并） ==================================
;;变换轴心点
(s/def ::pivot-center ::edit/pivot-center)
(s/def ::snap-options (s/nilable ::snap/snap-options))
;; 衰减编辑选项
(s/def ::falloff-options (s/nilable ::falloff/falloff-options))

(s/def ::canvas-record
  (s/keys :req-un [::session-id]
          :opt-un [::cursor-position
                   ::pivot-center
                   ::snap-options
                   ::falloff-options]))


(def session session.store/session)

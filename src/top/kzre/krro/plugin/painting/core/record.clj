(ns top.kzre.krro.plugin.painting.core.record
  (:require
   [clojure.spec.alpha :as s]
   [top.kzre.krro.curve.bezier2d.spec :as-alias bezier]
   [top.kzre.krro.plugin.painting.core.edit.falloff :as falloff]
   [top.kzre.krro.plugin.painting.core.edit.snap :as snap]
   [top.kzre.krro.plugin.painting.core.edit.spec :as edit]
   [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
   [top.kzre.krro.plugin.painting.core.store :as store]))

;; TODO 非画布记录，1. 合并全局数据 2. 做别的 record
(s/def ::canvas-id keyword?)

;; =============================== 项目数据 =====================================
(s/def ::canvas-data ::pc/canvas-data)

;; =============================== 应用全局数据(从 app.clj/session.clj 合并） ==================================
;;变换轴心点
(s/def ::pivot-center ::edit/pivot-center)
(s/def ::snap-options (s/nilable ::snap/snap-options))
;; 衰减编辑选项
(s/def ::falloff-options (s/nilable ::falloff/falloff-options))


;; ================================= 应用运行时数据 ====================================
(s/def ::cursor-position ::bezier/point)


(s/def ::canvas-record
  (s/keys :req-un [::canvas-id
                   ::canvas-data
                   ::canvas-state
                   ]
          :opt-un [::cursor-position
                   ::pivot-center
                   ::snap-options
                   ::falloff-options]))


(def record store/record)

(defn cursor-position [record]
  (get record :cursor-position))

(defn current-tool [record]
  (or (get-in record [:canvas-data :tool-data])
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



(ns top.kzre.krro.plugin.painting.core.edit.spec
  (:require
    [clojure.spec.alpha :as s]))

;;变换轴心点
(s/def ::pivot-center
  #{:aabb-center                                            ;; AABB 包围盒子中心
    :median-point                                           ;; 质心点
    :cursor                                                 ;; 游标工具
    :individual-point                                       ;; 各自使用自己的中心
    })

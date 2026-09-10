(ns top.kzre.krro.plugin.painting.core.brush.global
  (:require
    [taoensso.timbre :as log]))


(defonce default-brush
         {:dab          {:type :circle
                         :mask-type :hard}
          ;; 前景色
          :color        [0.2 0.3 0.56 0.65]
          ;; 动力学映射
          :dynamics     {:radius [{:sensor :pressure :curve :linear :min 0.5 :max 2.0 :mode :multiply}]}
          ;; DAB 间距
          :spacing      0.2
          :radius       3
          :blend-mode   :normal
          ;:taper-start-px   50
          ;:taper-end-px     50
          ;:taper-fields     []
          })

(defonce global-brush (atom default-brush))

(defn set-global-brush! [brush]
  (reset! global-brush brush))

(defn get-global-brush []
  (or @global-brush default-brush))

(defn set-global-brush-color! [color]
  (let [brush (get-global-brush)
        old-color (:color brush)
        ;; 从旧颜色中获取 alpha，若不存在则默认 1.0
        alpha (if (and (vector? old-color) (= 4 (count old-color)))
                (nth old-color 3)
                1.0)
        ;; 规范化新颜色为 RGBA
        new-color (cond
                    (and (vector? color) (= 3 (count color)))
                    (conj color alpha)

                    (and (vector? color) (= 4 (count color)))
                    color

                    :else
                    (throw (ex-info "Invalid color form" {})))]
    (log/debug "set global brush color: " new-color)
    (set-global-brush! (assoc brush :color (vec new-color)))))
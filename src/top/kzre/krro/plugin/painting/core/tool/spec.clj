(ns top.kzre.krro.plugin.painting.core.tool.spec
  (:require
   [clojure.spec.alpha :as s]))

(s/def ::tool-action #{:idle
                       :start :continue :commit :no-replace
                       :render
                       })
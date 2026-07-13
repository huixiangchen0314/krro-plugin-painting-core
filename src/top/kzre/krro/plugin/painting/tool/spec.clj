(ns top.kzre.krro.plugin.painting.tool.spec
  (:require
   [clojure.spec.alpha :as s]))


(s/def ::tool-action #{:idle :start :continue :commit})
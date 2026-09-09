(ns top.kzre.krro.plugin.painting.core.brush.core
  (:require
   [top.kzre.krro.plugin.painting.core.brush.effects]
   [top.kzre.krro.plugin.painting.core.brush.global :as global]
   [top.kzre.krro.plugin.painting.core.brush.events]))

(def default-brush global/default-brush)

(def global-brush global/global-brush)

(def set-global-brush! global/set-global-brush!)

(def get-global-brush global/get-global-brush)
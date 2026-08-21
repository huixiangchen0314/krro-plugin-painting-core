(ns top.kzre.krro.plugin.painting.core.layer.effects
  "副作用注册"
  (:require
    [top.kzre.krro.canvas.core.layer.core :as lc]
    [top.kzre.krro.core.reframe :as rf]
    [top.kzre.krro.plugin.painting.core.layer.util :as util]
    [top.kzre.krro.plugin.painting.core.ops.undo :as undo]
    [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
    [top.kzre.krro.plugin.painting.core.project.raster-layer :as pr]))


(rf/reg-fx
  :krro.painting :insert-layer-fx
  (fn [_ canvas-id path layer]
    (let [cd (pc/canvas-data! canvas-id)
          new-cd (util/insert-layer-at cd path layer)]
      (pc/save-canvas-data! canvas-id new-cd))))


(rf/reg-fx
  :krro.painting :save-raster-data-fx
  (fn [_ canvas-id layer-id]
    (let [layer (pc/find-layer-in-canvas! canvas-id layer-id)
          canvas (:canvas layer)]
      (pr/create-raster! layer-id canvas-id canvas))))

(rf/reg-fx
  :krro.painting :record-raster-layer-added
  (fn [_ canvas-id layer-id]
    (let [layers (pc/layers-by-id! canvas-id)
          path (lc/find-layer-path layer-id layers)
          layer (lc/find-layer-by-path path layers)]
      (undo/record-raster-layer-add! canvas-id path layer))))
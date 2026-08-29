(ns top.kzre.krro.plugin.painting.core.layer.effects
  "副作用注册"
  (:require
   [top.kzre.krro.canvas.core.layer.core :as lc]
   [top.kzre.krro.core.reframe :as rf]
   [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
   [top.kzre.krro.plugin.painting.core.project.raster-layer :as pr]
   [top.kzre.krro.plugin.painting.core.undo.core :as undo]
   [top.kzre.krro.plugin.painting.core.store :as store]))

(rf/reg-fx
  :krro.painting :save-raster-data-fx
  (fn [_ canvas-id layer-id]
    (let [layer (pc/find-layer-in-canvas! canvas-id layer-id)
          canvas (:canvas layer)]
      (pr/create-raster! layer-id canvas-id canvas))))

(rf/reg-fx
  :krro.painting :delete-raster-data-fx
  (fn [_ layer-id]
    (pr/delete-raster! layer-id)))

(rf/reg-fx
  :krro.painting :unchecked-create-raster-data
  (fn [_ layer-id canvas-id canvas]
    (pr/create-raster* layer-id canvas-id canvas)))

(rf/reg-fx
  :krro.painting :record-raster-layer-added
  (fn [_ canvas-id layer-id]
    (let [layers (pc/layers-by-id! canvas-id)
          path (lc/find-layer-path layer-id layers)
          layer (lc/find-layer-by-path path layers)]
      (undo/record-raster-layer-added! canvas-id path layer))))

(rf/reg-fx
  store/app-id :record-raster-layer-edited
  (fn [_ canvas-id layer-id old-canvas new-canvas dirty-tiles]
    (undo/record-raster-layer-edited! canvas-id layer-id
                                       old-canvas new-canvas
                                       dirty-tiles)))

(rf/reg-fx
  :krro.painting :record-canvas-edited
  (fn [_ canvas-id]
    (undo/record-canvas-edited! canvas-id)))


(ns top.kzre.krro.plugin.painting.core.schedule.composite
  "通用合成节点"
  (:require
    [top.kzre.krro.plugin.painting.core.model.tiled-image :as tiled-image]
    [top.kzre.krro.plugin.painting.core.render :as render]
    [top.kzre.krro.plugin.painting.core.schedule.protocol :as proto]
    [top.kzre.krro.plugin.painting.core.schedule.viewport-layer :as viewport-layer])
  (:import
    (java.util UUID)
    (java.util.concurrent CompletableFuture)
    (top.kzre.krro.util.tile TiledCanvas)))


(defrecord Composite [scheduler layers tex-atom]
  proto/IRenderNode
  (node-key [_]
    (mapv (fn [l] (proto/layer-id l)) layers))
  (request! [_ {:keys [canvas canvas-data dirty-tiles dirty-transform
                       viewport viewport-w viewport-h]}]
    (let [future (CompletableFuture.)
          tex (TiledCanvas. (.getTileSize canvas))]
      (swap! tex-atom
             (fn [c]
               (when c (.clear c))
               tex))
      (try
        (render/request-render-viewport!
          (proto/scheduler-id scheduler)
         (tiled-image/->TiledImage tex (:width canvas-data) (:height canvas-data))
          layers
          dirty-tiles dirty-transform
          viewport viewport-w viewport-h
          (fn []
            (.complete future
                       (viewport-layer/->ViewportLayer
                         (keyword (str "composite-" (UUID/randomUUID)))
                         true
                         tex
                         1.0
                         :normal))))
        (catch Throwable t
          (.completeExceptionally future t)))
      future)))
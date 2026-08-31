(ns top.kzre.krro.plugin.painting.core.edit.fill
  "填充工具（re-frame 事件驱动）"
  (:require
    [top.kzre.krro.canvas.core.layer.util :as util]
    [top.kzre.krro.core.reframe :as rf]
    [top.kzre.krro.plugin.painting.core.brush.core :as brush]
    [top.kzre.krro.plugin.painting.core.edit.interceptors :refer [cleanup-tool-interceptor]]
    [top.kzre.krro.plugin.painting.core.store :as store]
    [top.kzre.krro.plugin.painting.core.viewport :as vp]
    [top.kzre.krro.plugin.painting.core.tool.util :as tool-util])
  (:import
    (top.kzre.colorutils.color RGB)
    (top.kzre.krro.canvas.raster.bloodfill
      ColorMatchers
      FloodFillExecutor
      FloodFillRequest
      LineArtDetectors)
    (top.kzre.krro.util.math KMath)
    (top.kzre.krro.util.tile TiledCanvas)))

;; ── 辅助函数 ──────────────────────────────────

(defn- get-brush []
  (or @brush/global-brush brush/default-brush))

(defn- build-fill-request
  [canvas brush-spec local-x local-y width height]
  (let [fill-color (get brush-spec :color (RGB/rgba 0.0 0.0 0.0 1.0))
        tolerance  (float (get brush-spec :fill-tolerance 0.0))
        contiguous (get brush-spec :fill-contiguous true)
        anti-alias (get brush-spec :fill-anti-alias true)
        blend-mode (get brush-spec :fill-blend-mode nil)
        protect-opacity (get brush-spec :fill-protect-opacity false)
        expand-radius (float (get brush-spec :fill-expand-radius 0.0))
        protect-line-art (get brush-spec :fill-protect-line-art false)
        matcher (ColorMatchers/euclideanSquare)
        line-detector (LineArtDetectors/defaultDetector)
        mask nil]
    (-> (FloodFillRequest/newBuilder)
        (.targetCanvas canvas)
        (.seed (double local-x) (double local-y))
        (.fillColor (float-array fill-color))
        (.tolerance tolerance)
        (.contiguous contiguous)
        (.antiAlias anti-alias)
        (.blendMode blend-mode)
        (.protectOpacity protect-opacity)
        (.expandRadius expand-radius)
        (.protectLineArt protect-line-art)
        (.colorMatcher matcher)
        (.lineArtDetector line-detector)
        (.mask mask)
        (.size width height)
        (.build))))

;; ── 填充事件 ──────────────────────────────────

(rf/reg-event-fx
  store/app-id :fill-tool/press
  [(cleanup-tool-interceptor)]
  (fn [cofx [_ record-id event-map frame]]
    (let [record (:record cofx)
          layer-id (get-in record [:canvas-data :current-layer-id])
          layers (get-in record [:canvas-data :layers])
          layer (util/find-layer layer-id layers)]
      (if (= :raster (:type layer))
        (let [^TiledCanvas old-canvas (:canvas layer)
              layer-transform-inv (tool-util/layer-transform-inverse layer layers)
              layer-transform (KMath/mat2dInv layer-transform-inv)
              logic-pos (vp/screen->logic (vp/get-viewport frame)
                                          (:x event-map) (:y event-map))
              local-pos (util/transform-point layer-transform-inv
                                              (:x logic-pos) (:y logic-pos))
              brush-spec (get-brush)
              width (get-in record [:canvas-data :width])
              height (get-in record [:canvas-data :height])
              new-canvas (.copy old-canvas)
              request (build-fill-request new-canvas brush-spec
                                          (:x local-pos) (:y local-pos)
                                          width height)
              result (FloodFillExecutor/fill request)
              dirties (.getDirtyTiles result)]
          (when (and (.isChanged result) (seq dirties))
            (let [^TiledCanvas old-new-canvas (.copy new-canvas)
                  new-layer (assoc layer :canvas new-canvas)
                  new-layers (util/replace-layer new-layer layers)
                  new-record (-> record
                                 (assoc-in [:canvas-data :layers] new-layers))]
              {:record new-record
               :fx [[:record-raster-layer-edited record-id layer-id old-canvas old-new-canvas dirties]
                    [:render-canvas record-id dirties layer-transform]
                    ]})))
       {:fx [[:warn "Fill tool is only work for raster layer!"]]})
      )))
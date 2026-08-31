(ns top.kzre.krro.plugin.painting.core.edit.anchor
  (:require
    [top.kzre.krro.plugin.painting.core.edit.protocol :as p]
    [top.kzre.krro.plugin.painting.core.viewport :as vp]
    [top.kzre.krro.plugin.painting.core.tool.util :as tool-util])
  (:import (top.kzre.colorutils.color RGB)))

(defrecord AnchorState []
  p/IToolData
  (cleanup [_] nil)   ;; 无资源需要释放

  (overlay [_ ctx]
    (let [{:keys [viewport layer-path layer canvas-data]} ctx
          {:keys [layers]} canvas-data
          paths (:paths layer)
          path-order (:path-order layer [])
          ]
      (when (seq path-order)
        (mapcat (fn [path-id]
                  (let [path (get paths path-id)
                        {:keys [points closed?]} path]
                    ;; 生成每个控制点的 overlay 描述
                    (map (fn [point]
                           (let [screen-pos (vp/logic->screen viewport (:x point) (:y point))]
                             [:circle {:x (:x screen-pos)
                                       :y (:y screen-pos)
                                       :radius 5
                                       :fill-color (RGB/rgba 1 0 0 0.5)
                                       :stroke-color (RGB/rgba 1 0 0 1)
                                       :stroke-width 1.0}]))
                         points)))
                path-order)))))
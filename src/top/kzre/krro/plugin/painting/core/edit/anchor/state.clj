(ns top.kzre.krro.plugin.painting.core.edit.anchor.state
  (:require
   [top.kzre.krro.canvas.core.layer.util :as util]
   [top.kzre.krro.canvas.vector.core :as cv]
   [top.kzre.krro.core.reframe.transaction :as tx]
   [top.kzre.krro.plugin.painting.core.algo.segment]
   [top.kzre.krro.plugin.painting.core.edit.anchor.anchor-quadtree :as tree]
   [top.kzre.krro.plugin.painting.core.edit.protocol :as p]
   [top.kzre.krro.plugin.painting.core.transactions.anchor-extrude-modal :as anchor-extrude-modal]
   [top.kzre.krro.plugin.painting.core.transactions.anchor-width-adjust-modal :as tx-width]
   [top.kzre.krro.plugin.painting.core.viewport :as vp])
  (:import
    (top.kzre.colorutils.color RGB)
    (top.kzre.krro.canvas.vector.anchor Anchor)))


(defrecord AnchorState
  [^Anchor active-anchor               ;; 活动的锚点
   ^Anchor second-active-anchor        ;; 次要活动锚点
   selected-anchors                    ;; 被选择的锚点x
   selected-paths                      ;; 被选择的路径，用于控制锚点 overlay 显示
   ^Anchor hover-anchor                ;; 光标悬浮在的锚点
   last-layer-point
   ]
  p/IToolData
  (dispatch-event [_ {:keys [type]} cofx]
    (when-let [tx (tx/current cofx)]
      ;; 基于事务的分派
      (let [tx-kind (tx/kind tx)]
        (cond
          (and (= (anchor-extrude-modal/kind) tx-kind)
               (= type :move))
          :anchor/extrude-anchor

          (and (= (anchor-extrude-modal/kind) tx-kind)
               (= type :release))
          :anchor/exit-extrude-anchor-modal

          (and (= (tx-width/kind) tx-kind)
               (= type :move))
          :anchor/adjust-width
          (and (= (tx-width/kind) tx-kind)
               (= type :release))
          :anchor/exit-adjust-width-modal
          :else nil))))

  (cleanup! [_ ctx]
    (when-let [canvas-id (get-in ctx [:coeffects :record-id])]
      (tree/close canvas-id)))

  (overlay [_ {:keys [transaction transaction-kind
                      viewport layer layer-type layer-visible layer-transform]}]
    (when (= :vector layer-type)
      (if layer-visible
        (let [paths (cv/paths layer)
              path-order (cv/path-order layer)
              selected-set (or selected-anchors #{})
              ]
          (when (seq path-order)
            (concat

              ;; 锚点
              (mapcat
                (fn [path-id]
                  (let [path (get paths path-id)
                        curve (:curve path)
                        points (:points curve)]
                    (map-indexed
                      (fn [idx point]
                        (let [world-pos (util/transform-point layer-transform
                                                              (:x point) (:y point))
                              screen-pos (vp/logic->screen viewport
                                                           (:x world-pos)
                                                           (:y world-pos))
                              is-selected (some #(and (= (:path-id %) path-id)
                                                      (= (:point-idx %) idx))
                                                selected-set)
                              is-active (and (= (:path-id active-anchor) path-id)
                                             (= (:point-idx active-anchor) idx))]
                          [:circle {:x (:x screen-pos)
                                    :y (:y screen-pos)
                                    :radius (if is-selected 8 5)
                                    :fill-color (cond
                                                  is-active (RGB/rgba 1 1 0 0.9)
                                                  is-selected (RGB/rgba 1 1 0 0.8)
                                                  :else (RGB/rgba 1 0 0 0.5))
                                    :stroke-color (cond is-active (RGB/rgba 1 1 0 1)
                                                        is-selected (RGB/rgba 0 0 1 1)
                                                        :else (RGB/rgba 1 0 0 1))
                                    :stroke-width (if is-active 2.0 1.5)}]))
                      points)))
                path-order)
              )

            ))
        ;; 图层不可见时候不显示 overlay
        []))))

(defn make-anchor-state
  [& {:keys [_mode ]
      :or {}}]
  (map->AnchorState {}))
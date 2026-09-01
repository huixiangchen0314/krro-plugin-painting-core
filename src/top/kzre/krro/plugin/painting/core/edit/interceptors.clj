(ns top.kzre.krro.plugin.painting.core.edit.interceptors
  (:require [top.kzre.krro.canvas.core.layer.util :as util]
            [top.kzre.krro.plugin.painting.core.edit.protocol :as p]
            [top.kzre.krro.plugin.painting.core.tool.util :as tool-util]
            [top.kzre.krro.plugin.painting.core.viewport :as vp])
  (:import (top.kzre.krro.util.math KMath)))

(defn cleanup-tool-interceptor
  "返回一个 interceptor，在事件执行前检查并清理旧的工具状态（如果存在）。"
  ([] (cleanup-tool-interceptor nil))
  ([state-clz & {:keys [factory]}]
   {:before
    (fn [context]
      (let [record (get-in context [:coeffects :record])
            tool-data (get-in record [:canvas-state :tool-data])]
        (if (and tool-data
                 (or (nil? state-clz) (not (instance? state-clz tool-data))))
          ;; 工具存在，并且不是指定类型
          (do
            (when (satisfies? p/IToolData tool-data)
              (p/cleanup! tool-data))
            (let [new-tool-data (when factory (factory context))]
              (assoc-in context [:coeffects :record :canvas-state :tool-data] new-tool-data)))
          ;; 无旧数据，直接放行
          context)))}))


(defn tool-context-interceptor
  "为工具事件提供标准化的上下文数据，包括画布、图层、视口和坐标转换。
   将上下文注入到 coeffects 的 :krro.painting/tool-context 键下。"
  []
  {:before
   (fn [context]
     (let [cofx (:coeffects context)
           {:keys [record event]} cofx
           [_ record-id event-map frame] event

           ;; 基础数据
           canvas-data (:canvas-data record)
           current-layer-id (:current-layer-id canvas-data)
           layers (get-in record [:canvas-data :layers])

           ;; 图层信息
           layer-path (when current-layer-id (util/find-layer-path current-layer-id layers))
           layer (when layer-path (util/find-layer-by-path layer-path layers))
           layer-type (when layer (:type layer))
           layer-visible (when layer (:visible layer))
           layer-transform-inv (when layer (tool-util/layer-transform-inverse layer layers))
           layer-transform (when layer-transform-inv (KMath/mat2dInv layer-transform-inv))

           ;; 视口
           viewport (vp/get-viewport frame)

           ;; 坐标转换（仅当事件包含坐标时）
           canvas-point (when (and event-map (:x event-map) (:y event-map))
                          (vp/screen->logic viewport (:x event-map) (:y event-map)))
           canvas-event (if canvas-point
                          (assoc event-map :x (:x canvas-point) :y (:y canvas-point))
                          event-map)

           layer-event (if (and layer-transform-inv canvas-point)
                         (let [layer-point (util/transform-point layer-transform-inv
                                                                 (:x canvas-point)
                                                                 (:y canvas-point))]
                           (assoc event-map :x (:x layer-point) :y (:y layer-point)))
                         event-map)
           now (:timestamp event-map)
           last-press-time (get-in record [:canvas-state :last-press-time])
           click-threshold 300
           ;; ---- 点击计数维护 ----
           click-count (cond
                         (= :release (:type event-map))
                         (let [is-click (when last-press-time (< (- now last-press-time) click-threshold))
                               second-last-press-time (get-in record [:canvas-state :second-last-press-time])
                               is-double (when (and is-click second-last-press-time)
                                              (< (- last-press-time second-last-press-time) click-threshold))
                               ]
                           (cond
                             is-double 2
                             is-click 1
                             :else 0))
                         :else 0)   ; 其他事件不处理

           ;; 更新 record（如果 click-count 处理过）
           updated-record (cond
                            (= :press (:type event-map))
                            (-> record
                                (assoc-in [:canvas-state :last-press-time] now)
                                (assoc-in [:canvas-state :second-last-press-time] last-press-time))
                            :else record)
           ]
       (-> context
           (assoc-in [:coeffects :record] updated-record)
           (assoc-in [:coeffects :krro.painting/tool-context]
                     {:event               event-map
                      :canvas-event        canvas-event
                      :layer-event         layer-event
                      :viewport            viewport
                      :canvas-id           record-id
                      :layer-id            current-layer-id
                      :layer-path          layer-path
                      :layer-type          layer-type
                      :layer-visible       layer-visible
                      :layer               layer
                      :layer-transform     layer-transform
                      :layer-transform-inv layer-transform-inv
                      :layers              layers
                      :click-count         click-count
                      :canvas-data         canvas-data}))))})

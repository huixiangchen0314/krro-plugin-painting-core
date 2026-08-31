(ns top.kzre.krro.plugin.painting.core.edit.interceptors
  (:require [top.kzre.krro.canvas.core.layer.util :as util]
            [top.kzre.krro.plugin.painting.core.edit.common :as common]
            [top.kzre.krro.plugin.painting.core.viewport :as vp]
            [top.kzre.krro.plugin.painting.core.tool.util :as tool-util])
  (:import (top.kzre.krro.util.math KMath)))

(defn cleanup-tool-interceptor
  "返回一个 interceptor，在事件执行前检查并清理旧的工具状态（如果存在）。"
  []
  {:before
   (fn [context]
     (let [record (get-in context [:coeffects :record])
           tool-data (get-in record [:canvas-state :tool-data])]
       (if tool-data
         ;; 存在旧工具数据，执行清理
         (let [new-record (common/cleanup-tool-data! record)]
           (assoc-in context [:coeffects :record] new-record))
         ;; 无旧数据，直接放行
         context)))})


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
                         event-map)]

       (assoc-in context
                 [:coeffects :krro.painting/tool-context]
                 {:event               event-map
                  :canvas-event        canvas-event
                  :layer-event         layer-event
                  :viewport            viewport
                  :canvas-id           record-id
                  :layer-id            current-layer-id
                  :layer-path          layer-path
                  :layer               layer
                  :layer-transform     layer-transform
                  :layer-transform-inv layer-transform-inv
                  :layers              layers
                  :canvas-data         canvas-data})))})
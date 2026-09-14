(ns top.kzre.krro.plugin.painting.core.schedule.scheduler-impl
  (:require
   [top.kzre.krro.plugin.painting.core.schedule.protocol :as proto]
   [top.kzre.krro.plugin.painting.core.schedule.raster-layer :as raster-layer]
   [top.kzre.krro.plugin.painting.core.schedule.composite :as composite]))

(defn build-graphs [layers]
  (let [layer-nodes
        (mapv
          #(raster-layer/make-raster-layer-node %) layers)]
    (composite/make-composite-node layer-nodes)))


(defrecord SchedulerState [root-node])

(defn make-state
  ([]
   (map->SchedulerState {}))
  ([root-node]
   (->SchedulerState root-node)))

(defrecord RenderScheduler [state-atom]
  proto/IRenderScheduler
  (diff! [_ layers ctx]
    (reset! state-atom
           (make-state (build-graphs layers))))
  (render [_ ctx]
    (when-let [node (:root-node @state-atom)]
      (proto/request! node ctx))))

(defn make-scheduler []
  (->RenderScheduler (atom (make-state))))
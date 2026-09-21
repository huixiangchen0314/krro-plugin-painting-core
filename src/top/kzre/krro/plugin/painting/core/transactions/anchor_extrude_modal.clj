(ns top.kzre.krro.plugin.painting.core.transactions.anchor-extrude-modal
  (:require
   [top.kzre.krro.canvas.vector.core :as cv]
   [top.kzre.krro.core.reframe.transaction :as tx]
   [top.kzre.krro.plugin.painting.core.record :as record])
  (:import
   (top.kzre.krro.canvas.vector.anchor Anchor)))

(defonce ^:private kind* ::anchor-extrude-modal)

(defn kind [] kind*)

(defrecord AnchorExtrudeModalTransaction
  [layer-backup                                             ;; 图层备份
   ^Anchor end-anchor                                       ;;用于挤出的端点锚点
   ^Anchor new-anchor                                       ;; 挤出的新锚点
   ^Anchor updated-anchor                                   ;; 旧锚点的更新
   init-point                                               ;; 初始事件点
   ]
  tx/ITransaction
  (begin [this {:keys [end-anchor layer-event]}
          record]
    (let [{:keys [layer]} (record/layer-context record)]
      [(assoc this
         :layer-backup layer
         :end-anchor end-anchor
         :init-point (select-keys layer-event [:x :y]))
       {:fx [[:tool/set-command-enabled false]]}]))
  (operate [_ op-kind
            {:keys [layer-event]} record]
    (case op-kind
      :move
      nil
      (throw (ex-info "unknown op-kind for transaction vector-stroke" {:op-kind op-kind}))))

  (rollback [_ _ record]
    (let [{:keys [canvas-id layer-id layer]}
          (record/layer-context record)]
      {:dispatch [:oplog/vector-layer-paths-dirty
                  canvas-id layer-id
                  (cv/paths layer)
                  (cv/paths layer-backup)
                  :undo? false]
       :fx       [[:tool/set-command-enabled true]]})))

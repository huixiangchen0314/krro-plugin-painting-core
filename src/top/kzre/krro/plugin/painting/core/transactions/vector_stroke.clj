(ns top.kzre.krro.plugin.painting.core.transactions.vector-stroke
  "矢量笔触事务。

   stroke 累积、layer-backup 起点快照、preview-path-id 固定、path-style 从应用层传入。

   每次 drag / commit：
     - 从 record 当前 paths 删除 preview-path-id（先删）
     - 渲染新 path，加入 preview-path-id（后加）
     - 用 dispatch-n 分发两个 change

   rollback：
     - 从 layer-backup 全量恢复"
  (:require
    [top.kzre.krro.canvas.vector.core :as cv]
    [top.kzre.krro.core.reframe.transaction :as tx]
    [top.kzre.krro.plugin.painting.core.layer.clone :as clone]
    [top.kzre.krro.plugin.painting.core.project.vector-layer :as pv]
    [top.kzre.krro.plugin.painting.core.record :as record]
    [top.kzre.krro.plugin.painting.core.tool.stroke :as stroke])
  (:import
    (top.kzre.krro.brush Stroke)))

(defonce ^:private transaction-kind* ::vector-stroke)
(defn kind [] transaction-kind*)

(defn- append-point
  [^Stroke stroke layer-event]
  (.append stroke (stroke/->pointer-event layer-event)))

(defrecord VectorStrokeTransaction
  [^Stroke stroke
   layer-backup
   stroke-path-id
   path-style]

  tx/ITransaction
  (kind [_] (kind))

  (begin [_ {:keys [style]} record]
    (let [{:keys [layer]} (record/layer-context record)]
      ;; 事务是本地相关的，如果要支持远程会话，再开reframe
      [(->VectorStrokeTransaction
         (stroke/make-stroke)
         (clone/clone-layer layer)
         (cv/fresh-path-id)
         style)
       {:fx [[:tool/set-command-enabled false]]}]))

  (operate [this op-kind {:keys [layer-event]} record]
    (case op-kind
      :drag
      (let [new-stroke (append-point stroke layer-event)
            {:keys [canvas-id layer-id]} (record/layer-context record)
            new-t      (assoc this :stroke new-stroke)]
        [new-t
         {:dispatch
          [:oplog/vector-stroke
           canvas-id layer-id new-stroke path-style
           :path-id stroke-path-id
           :undo? false]
          :fx [[:tool/set-command-enabled false]]}])
      (throw (ex-info "unknown op-kind for transaction vector-stroke" {:op-kind op-kind}))))

  (commit [_ _ record]
    (let [{:keys [canvas-id layer-id]} (record/layer-context record)]
      {:dispatch
       [:oplog/vector-stroke
        canvas-id layer-id  stroke path-style
        :path-id stroke-path-id
        :undo? true]
       :fx [[:tool/set-command-enabled true]]}))

  (rollback [_ _ record]
    (let [{:keys [canvas-id layer-id layer]} (record/layer-context record)]
      {:dispatch [:oplog/vector-layer-paths-dirty
                  canvas-id layer-id
                  (pv/paths layer)
                  (pv/paths layer-backup)
                  :undo? false]
       :fx [[:tool/set-command-enabled true]]})))

(tx/reg-transaction (kind)
                    (map->VectorStrokeTransaction {}))
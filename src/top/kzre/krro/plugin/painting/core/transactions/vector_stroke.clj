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
   [top.kzre.krro.brush.vector :as vec-brush]
   [top.kzre.krro.core.reframe.transaction :as tx]
   [top.kzre.krro.curve.bezier2d.core :as bezier]
   [top.kzre.krro.plugin.painting.core.layer.clone :as clone]
   [top.kzre.krro.plugin.painting.core.project.vector-layer :as pv]
   [top.kzre.krro.plugin.painting.core.record :as record]
   [top.kzre.krro.plugin.painting.core.tool.stroke :as stroke]
   [top.kzre.krro.canvas.vector.core :as canvas.vector])
  (:import
    (top.kzre.curve.bezier2d ArcLengthUtils Curve TableMapping)
   (top.kzre.krro.brush Stroke)))

(defonce ^:private transaction-kind* ::vector-stroke)
(defn kind [] transaction-kind*)


(defn- append-point
  [^Stroke stroke layer-event]
  (.append stroke (stroke/->pointer-event layer-event)))

(defn- vector-stroke->bezier-path
  [{:keys [^Curve curve width-samples t-params]} style]
  (let [arc-params (TableMapping/uniformSParams
                      (ArcLengthUtils/buildArcLengthParams curve (double-array t-params)))]
    {:path-type     :bezier
     :curve  (bezier/curve->edn curve)
     :style         style
     :t-params      t-params
     :width-samples width-samples
     :arc-params    arc-params}))

(defn- render-stroke-to-path
  [stroke style]
  (when-let [result (vec-brush/render-vector-stroke (.getStroke stroke))]
    (vector-stroke->bezier-path result style)))


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
         (canvas.vector/fresh-path-id)
         style)
       {:fx [[:tool/set-command-enabled false]]}]))

  (operate [this op-kind {:keys [layer-event]} record]
    (case op-kind
      :drag
      (let [new-stroke (append-point stroke layer-event)]
        (or
          (when (> (.size new-stroke) (.size stroke))
            (let [{:keys [canvas-id layer-id]} (record/layer-context record)
                  new-path   (render-stroke-to-path new-stroke path-style)
                  new-t      (assoc this :stroke new-stroke)]
              (if new-path
                [new-t
                 {:dispatch
                  [:oplog/vector-path-updated
                   canvas-id layer-id stroke-path-id new-path
                   :undo? true]}]
                [new-t {}])))
          [this {}]))
      (throw (ex-info "unknown op-kind for transaction vector-stroke" {:op-kind op-kind}))))

  (commit [_ _ record]
    (let [{:keys [canvas-id layer-id]} (record/layer-context record)
          new-path   (when (> (.size stroke) 0)
                       (render-stroke-to-path stroke path-style))]
      (if new-path
        {:dispatch
         [:oplog/vector-path-updated
          canvas-id layer-id stroke-path-id new-path
          :undo? true]
         :fx [[:tool/set-command-enabled true]]}
        {:fx [[:tool/set-command-enabled true]]})))

  (rollback [_ _ record]
    (let [{:keys [canvas-id layer-id layer]}
          (record/layer-context record)]
      {:dispatch [:oplog/vector-layer-paths-dirty
                  canvas-id layer-id
                  (pv/paths layer)
                  (pv/paths layer-backup)
                  :undo? false]
       :fx [[:tool/set-command-enabled true]]})))

(tx/reg-transaction (kind)
                    (->VectorStrokeTransaction nil nil nil nil))
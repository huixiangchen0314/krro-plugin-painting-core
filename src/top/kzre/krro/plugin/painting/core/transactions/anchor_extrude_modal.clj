(ns top.kzre.krro.plugin.painting.core.transactions.anchor-extrude-modal
  "锚点挤出模态事务。

   begin 原地挤出（新锚点在端点位置）——operate 平移新锚点——commit 打 undo 标记。"
  (:require
    [taoensso.timbre :as log]
    [top.kzre.krro.canvas.vector.core :as cv]
    [top.kzre.krro.core.reframe.transaction :as tx]
    [top.kzre.krro.plugin.painting.core.record :as record])
  (:import
    (top.kzre.krro.canvas.vector.anchor Anchor)))

(defonce ^:private kind* ::anchor-extrude-modal)
(defn kind [] kind*)

(defrecord AnchorExtrudeModalTransaction
  [layer-backup            ; 进入时的 layer 快照——rollback 用
   ^Anchor end-anchor      ; 挤出的端点锚点
   ^Anchor new-anchor      ; 挤出后新锚点（begin 时预计算索引）
   last-point]             ; 上次鼠标位置——算增量
  tx/ITransaction
  (kind [_] (kind))

  (begin [this {:keys [anchor]} record]
    (let [{:keys [canvas-id layer-id layer]} (record/layer-context record)
          paths   (cv/paths layer)
          path-id (:path-id anchor)]
      (cond
        (nil? anchor)
        (do (log/info "anchor-extrude: no anchor"
                      {:canvas-id canvas-id :layer-id layer-id})
            [nil {}])

        (nil? path-id)
        (do (log/info "anchor-extrude: anchor has no path-id"
                      {:canvas-id canvas-id :layer-id layer-id :anchor anchor})
            [nil {}])

        (not (cv/end-anchor? paths anchor))
        (do (log/info "anchor-extrude: anchor is not an endpoint"
                      {:canvas-id  canvas-id
                       :layer-id   layer-id
                       :anchor     anchor
                       :path-id    path-id})
            [nil {}])

        :else
        (let [path       (get paths path-id)
              n          (count (cv/path-points path))
              is-start?  (zero? (:point-idx anchor))
              new-idx    (if is-start? 0 n)
              new-anchor (cv/->Anchor path-id new-idx)
              end-point  (cv/anchor-point paths anchor)]
          [(assoc this
             :layer-backup layer
             :end-anchor   anchor
             :new-anchor   new-anchor
             :last-point   end-point)
           {:dispatch [:oplog/anchor-extrude
                       canvas-id layer-id
                       anchor
                       :point end-point
                       :undo? false]
            :fx [[:tool/set-command-enabled false]]}]))))

  (operate [this op-kind {:keys [layer-event]} record]
    (case op-kind
      :move
      (let [{:keys [canvas-id layer-id]} (record/layer-context record)
            {new-anchor :new-anchor last-point :last-point} this
            dx (- (:x layer-event) (:x last-point))
            dy (- (:y layer-event) (:y last-point))]
        [(assoc this :last-point (select-keys layer-event [:x :y]))
         {:dispatch [:oplog/anchor-translate
                     canvas-id layer-id
                     [new-anchor]
                     dx dy
                     :undo? false]}])
      (throw (ex-info "unknown op-kind" {:op-kind op-kind :kind (kind)}))))

  (commit [this _ record]
    (let [{:keys [canvas-id layer-id]} (record/layer-context record)
          {new-anchor :new-anchor} this]
      ;; no-op translate 打 undo 标记——触发 record! 记录整段事务
      {:dispatch [:oplog/anchor-translate
                  canvas-id layer-id
                  [new-anchor]
                  0 0
                  :undo? true]
       :fx [[:tool/set-command-enabled true]]}))

  (rollback [this _ record]
    (let [{:keys [canvas-id layer-id layer]} (record/layer-context record)
          {layer-backup :layer-backup} this]
      {:dispatch [:oplog/vector-layer-paths-dirty
                  canvas-id layer-id
                  (cv/paths layer)
                  (cv/paths layer-backup)
                  :undo? false]
       :fx [[:tool/set-command-enabled true]]})))

(tx/reg-transaction (kind)
                    (map->AnchorExtrudeModalTransaction {}))
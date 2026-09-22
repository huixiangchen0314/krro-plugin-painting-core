(ns top.kzre.krro.plugin.painting.core.transactions.anchor-width-adjust-modal
  "宽度调整模态事务。

   进入模态 → 每次 move 从 layer-backup 重算宽度 → 退出 commit / cancel。

   幂等：每次 operate 派发「从 begin 到当前的累积宽度变化」——
   基于 layer-backup 重算——不累积误差。"
  (:require
   [top.kzre.krro.canvas.vector.core :as cv]
   [top.kzre.krro.core.custom :as custom]
   [top.kzre.krro.core.reframe.transaction :as tx]
   [top.kzre.krro.plugin.painting.core.record :as record]
   [top.kzre.krro.plugin.painting.core.transactions.vector-layer :as tx.vector-layer])
  (:import
   (top.kzre.krro.canvas.vector.anchor Anchor)))

(custom/defcustom :krro.painting.anchor/adjust-width-sensitivity
                  0.1
                  :type :double
                  :group :krro.painting.anchor
                  :doc "Width adjustment sensitivity factor. Larger values make width changes more responsive to mouse movement.")


(defonce ^:private kind* ::anchor-width-adjust-modal)
(defn kind [] kind*)
;; T 宽度采样的编辑，
;; 1. 必定为衰减编辑
;; 2.
(defn- process-width-adjust
  [{:keys [init-distance sensitivity selected-anchors layer-backup] :as tx}
   distance record commit?]
  (let [{:keys [canvas-id layer-id]} (record/layer-context record)
        total-delta (* (- distance init-distance) sensitivity)

        effects
        {:dispatch [:oplog/anchor-width-adjust
                    canvas-id layer-id selected-anchors total-delta
                    :paths (cv/paths layer-backup)
                    :undo? commit?]
         :fx [[:tool/set-command-enabled commit?]]}]
    (if commit?
      effects
      [(when-not commit? tx) effects])))

(defrecord AnchorWidthAdjustModalTransaction
  [layer-backup            ; 进入时的 layer 快照
   ^Anchor active-anchor   ; 活动锚点——距离参考
   selected-anchors        ; 受影响的锚点集
   sensitivity             ; 灵敏度——begin 时读一次
   init-distance]          ; 进入时的屏幕距离
  tx/ITransaction
  (kind [_] (kind))

  (begin [this kwargs record]
    (let [{:keys [layer]} (record/layer-context record)]
      [(assoc this
         :layer-backup     layer
         :active-anchor    (:active-anchor kwargs)
         :selected-anchors (:selected-anchors kwargs)
         :sensitivity      (abs (custom/get-custom :krro.painting.anchor/adjust-width-sensitivity))
         :init-distance    (:distance kwargs))
       {:fx [[:tool/set-command-enabled false]]}]))

  (operate [this op-kind {:keys [distance]} record]
    {:pre [number? distance]}
    (case op-kind
      :move
      (process-width-adjust this distance record false)
      (throw (ex-info "unknown op-kind"
                      {:op-kind op-kind :kind (kind)}))))

  (commit [this {:keys [distance]} record]
    {:pre [number? distance]}
    (process-width-adjust this distance record true))

  (rollback [_ _ record]
    (tx.vector-layer/rollback-effect record layer-backup)))

(tx/reg-transaction (kind)
                    (map->AnchorWidthAdjustModalTransaction {}))
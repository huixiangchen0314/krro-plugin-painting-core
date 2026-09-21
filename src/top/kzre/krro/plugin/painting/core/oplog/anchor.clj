(ns top.kzre.krro.plugin.painting.core.oplog.anchor
  "锚点 oplog 事件。

   依赖锚点的挂表——本质还是路径改变。

   每个事件接收**操作意图**——内部调用 canvas.vector 领域函数算出结果——
   构造 change 派发到 :oplog/log。

   事件按关注点分组：
     几何   —— 锚点位置变化（translate / rotate / scale / mirror / skew）
     拓扑   —— 控制点数量 / 结构变化（insert / delete / extrude / weld）
     属性   —— 手柄 / 连续性 / 宽度

   所有事件产出的 change 都在 changes/path —— 不感知曲线类型。"
  (:require
    [top.kzre.krro.core.reframe.core :as rf]
    [top.kzre.krro.plugin.painting.core.store :as store])
  (:import
    (top.kzre.krro.canvas.vector.anchor Anchor)))

;; ═══════════════════════════════════════════════
;; 几何 —— 锚点位置变化
;; ═══════════════════════════════════════════════

(rf/reg-event-fx
  store/app-id :oplog/anchor-translate
  (fn [cofx [_ canvas-id layer-id anchors dx dy
             & {:keys [path-ids
                       snap-opts falloff-opts pivot-opts]
                :as ctx}]]
    ;; TODO:
    ;;   1. 取 layer / old-paths
    ;;   2. 若 :falloff-opts 存在 → 在 path-ids 范围内算 affected（按距离加权）
    ;;      否则 → affected = anchors（统一位移）
    ;;   3. 应用位移 → new-paths
    ;;   4. change/make-vector-anchor-positions-changed
    ;;   5. dispatch :oplog/log
    ))

(rf/reg-event-fx
  store/app-id :oplog/anchor-rotate
  (fn [cofx [_ canvas-id layer-id anchors center angle
             & {:keys [snap-opts falloff-opts pivot-opts]
                :as ctx}]]
    ;; TODO: 调 v/rotate-anchors → change/make-vector-anchor-positions-changed
    ))

(rf/reg-event-fx
  store/app-id :oplog/anchor-scale
  (fn [cofx [_ canvas-id layer-id anchors center sx sy
             & {:keys [snap-opts falloff-opts pivot-opts]
                :as ctx}]]
    ;; TODO: 调 v/scale-anchors → change/make-vector-anchor-positions-changed
    ))

(rf/reg-event-fx
  store/app-id :oplog/anchor-mirror
  (fn [cofx [_ canvas-id layer-id anchors axis
             & {:keys [snap-opts falloff-opts pivot-opts]
                :as ctx}]]
    ;; TODO: 调 v/mirror-anchors → change/make-vector-anchor-positions-changed
    ))

(rf/reg-event-fx
  store/app-id :oplog/anchor-skew
  (fn [cofx [_ canvas-id layer-id anchors center kx ky
             & {:keys [snap-opts falloff-opts pivot-opts]
                :as ctx}]]
    ;; TODO: 调 v/skew-anchors → change/make-vector-anchor-positions-changed
    ))

;; ═══════════════════════════════════════════════
;; 拓扑 —— 控制点数量 / 结构变化
;; ═══════════════════════════════════════════════

(rf/reg-event-fx
  store/app-id :oplog/anchor-insert
  (fn [cofx [_ canvas-id layer-id path-id t & {:as ctx}]]
    ;; TODO: 调 v/insert-anchor → change/make-vector-anchor-inserted
    ))

(rf/reg-event-fx
  store/app-id :oplog/anchor-delete
  (fn [cofx [_ canvas-id layer-id anchor & {:as ctx}]]
    ;; TODO: 调 v/delete-anchor → change/make-vector-anchor-deleted
    ))

(rf/reg-event-fx
  store/app-id :oplog/anchor-extrude
  (fn [cofx [_ canvas-id layer-id end-anchor
             & {:keys [point]
                :as ctx}]]
    ;; TODO:
    ;;   point 缺省时 = end-anchor 的当前位置（原地挤出）
    ;;   调 v/extrude-anchor → change/make-vector-anchor-inserted
    ))

(rf/reg-event-fx
  store/app-id :oplog/anchor-weld
  (fn [cofx [_ canvas-id layer-id active-anchor passive-anchor & {:as ctx}]]
    ;; TODO: 调 v/weld-anchors → change/make-vector-anchor-positions-changed
    ;;                              或 change/make-vector-paths-geometry-changed（跨路径）
    ))

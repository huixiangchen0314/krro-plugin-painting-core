(ns top.kzre.krro.plugin.painting.core.changes.vector-layer
  (:require
   [top.kzre.krro.canvas.core.layer.util :as util]
   [top.kzre.krro.core.util.diff :as diff]
   [top.kzre.krro.plugin.painting.core.changes.composite :as composite]
   [top.kzre.krro.plugin.painting.core.oplog.protocol :as proto]
   [top.kzre.krro.plugin.painting.core.project.vector-layer :as pv]
   [top.kzre.krro.plugin.painting.core.record :as record]
   [top.kzre.krro.plugin.painting.core.undo.core :as undo])
  (:import
    (java.lang AutoCloseable)
    (top.kzre.krro.plugin.painting.core.algo.anchor Anchor)))

(defn- apply-layer-change
  [record new-layer layers]
  (assoc-in record [:canvas-data :layers]
            (util/replace-layer new-layer layers)))

;; ═══════════════════════════════════════════════
;; VectorAnchorsEdited —— 锚点数据变，拓扑不变
;; anchors: [Anchor ...] —— 每个 Anchor 自带 :path-id / :point-idx
;; 支持跨 path——按 path-id 分组发 quadtree fx
;; ═══════════════════════════════════════════════

(defrecord VectorAnchorsEdited
  [layer-id old-paths new-paths anchors
   dirty-tiles dirty-transform]
  AutoCloseable
  (close [_] nil)

  diff/IChange
  (seeds [_] layer-id)
  (empty-change? [_] (= old-paths new-paths))
  (combine [this other] (composite/composite-change this other))

  proto/IOperation
  (realize [this record]
    (let [{:keys [canvas-id layer layers]} (record/layer-context record layer-id)
          new-layer (pv/assoc-paths layer new-paths)
          by-path   (group-by :path-id anchors)
          quadtree-fx
          (mapv (fn [[path-id path-anchors]]
                  [:anchor-quadtree/update-anchors canvas-id path-id
                   (get old-paths path-id) (get new-paths path-id) path-anchors])
                by-path)]
      [(apply-layer-change record new-layer layers)
       (into [[:render-canvas canvas-id this]] quadtree-fx)]))

  (record! [_ record]
    (let [{:keys [canvas-id]} record]
      (undo/record-canvas-edited! canvas-id layer-id))))
;; ═══════════════════════════════════════════════
;; VectorAnchorInserted —— 锚点插入（中间插入 / 端点挤出）
;; anchor 自带 :path-id / :point-idx
;; 消费端根据 anchor 的 point-idx 区分：
;;   - 0 或末尾   → 端点挤出
;;   - 中间        → 中间插入
;; ═══════════════════════════════════════════════

(defrecord VectorAnchorInserted
  [layer-id old-paths new-paths ^Anchor anchor
   dirty-tiles dirty-transform]
  AutoCloseable
  (close [_] nil)

  diff/IChange
  (seeds [_] layer-id)
  (empty-change? [_] (= old-paths new-paths))
  (combine [this other] (composite/composite-change this other))

  proto/IOperation
  (realize [this record]
    (let [{:keys [canvas-id]} record
          {:keys [layer layers]} (record/layer-context record layer-id)
          new-layer (pv/assoc-paths layer new-paths)
          path-id   (:path-id anchor)]
      [(apply-layer-change record new-layer layers)
       [[:render-canvas canvas-id this]
        [:anchor-quadtree/delete-path canvas-id path-id (get old-paths path-id)]
        [:anchor-quadtree/insert-path canvas-id path-id (get new-paths path-id)]]]))

  (record! [_ record]
    (let [{:keys [canvas-id]} record]
      (undo/record-canvas-edited! canvas-id layer-id))))

;; ═══════════════════════════════════════════════
;; VectorAnchorDeleted —— 删除锚点
;; anchor 自带 :path-id
;; ═══════════════════════════════════════════════

(defrecord VectorAnchorDeleted
  [layer-id old-paths new-paths ^Anchor anchor
   dirty-tiles dirty-transform]
  AutoCloseable
  (close [_] nil)

  diff/IChange
  (seeds [_] layer-id)
  (empty-change? [_] (= old-paths new-paths))
  (combine [this other] (composite/composite-change this other))

  proto/IOperation
  (realize [this record]
    (let [{:keys [canvas-id]} record
          {:keys [layer layers]} (record/layer-context record layer-id)
          new-layer (pv/assoc-paths layer new-paths)
          path-id   (:path-id anchor)]
      [(apply-layer-change record new-layer layers)
       [[:render-canvas canvas-id this]
        [:anchor-quadtree/delete-path canvas-id path-id (get old-paths path-id)]
        [:anchor-quadtree/insert-path canvas-id path-id (get new-paths path-id)]]]))

  (record! [_ record]
    (let [{:keys [canvas-id]} record]
      (undo/record-canvas-edited! canvas-id layer-id))))

;; ═══════════════════════════════════════════════
;; VectorPathAdded —— 单个路径新增
;; path 自带 :id
;; ═══════════════════════════════════════════════

(defrecord VectorPathAdded
  [layer-id old-paths new-paths path
   dirty-tiles dirty-transform]
  AutoCloseable
  (close [_] nil)

  diff/IChange
  (seeds [_] layer-id)
  (empty-change? [_] (= old-paths new-paths))
  (combine [this other] (composite/composite-change this other))

  proto/IOperation
  (realize [this record]
    (let [{:keys [canvas-id layer layers]} (record/layer-context record layer-id)
          new-layer (pv/assoc-paths layer new-paths)
          path-id   (:id path)]
      [(apply-layer-change record new-layer layers)
       [[:render-canvas canvas-id this]
        [:anchor-quadtree/insert-path canvas-id path-id path]]]))

  (record! [_ record]
    (let [{:keys [canvas-id]} record]
      (undo/record-canvas-edited! canvas-id layer-id))))

;; ═══════════════════════════════════════════════
;; VectorPathRemoved —— 单个路径删除
;; 被删路径已不在 new-paths 里——只能携带 path-id
;; ═══════════════════════════════════════════════

(defrecord VectorPathRemoved
  [layer-id old-paths new-paths path-id
   dirty-tiles dirty-transform]
  AutoCloseable
  (close [_] nil)

  diff/IChange
  (seeds [_] layer-id)
  (empty-change? [_] (= old-paths new-paths))
  (combine [this other] (composite/composite-change this other))

  proto/IOperation
  (realize [this record]
    (let [{:keys [canvas-id layer layers]} (record/layer-context record layer-id)
          new-layer (pv/assoc-paths layer new-paths)]
      [(apply-layer-change record new-layer layers)
       [[:render-canvas canvas-id this]
        [:anchor-quadtree/delete-path canvas-id path-id
         (get old-paths path-id)]]]))

  (record! [_ record]
    (let [{:keys [canvas-id]} record]
      (undo/record-canvas-edited! canvas-id layer-id))))

;; ═══════════════════════════════════════════════
;; VectorLayerPathsDirty —— 全量替换（回滚 / 导入）
;; ═══════════════════════════════════════════════

(defrecord VectorLayerPathsDirty
  [layer-id old-paths new-paths
   dirty-tiles dirty-transform]
  AutoCloseable
  (close [_] nil)

  diff/IChange
  (seeds [_] layer-id)
  (empty-change? [_] (= old-paths new-paths))
  (combine [this other] (composite/composite-change this other))

  proto/IOperation
  (realize [this record]
    (let [{:keys [canvas-id layer layers]} (record/layer-context record layer-id)
          new-layer  (pv/assoc-paths layer new-paths)
          new-layers (util/replace-layer new-layer layers)]
      [(assoc-in record [:canvas-data :layers] new-layers)
       [[:render-canvas canvas-id this]
        [:anchor-quadtree/rebuild canvas-id layer-id new-paths]]]))

  (record! [_ record]
    (let [{:keys [canvas-id]} record]
      (undo/record-canvas-edited! canvas-id layer-id))))
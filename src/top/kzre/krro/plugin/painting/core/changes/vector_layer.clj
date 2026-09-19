(ns top.kzre.krro.plugin.painting.core.changes.vector-layer
  (:require
   [top.kzre.krro.canvas.core.layer.util :as util]
   [top.kzre.krro.core.util.diff :as diff]
   [top.kzre.krro.plugin.painting.core.changes.composite :as composite]
   [top.kzre.krro.plugin.painting.core.oplog.protocol :as proto]
   [top.kzre.krro.plugin.painting.core.project.vector-layer :as pv]
   [top.kzre.krro.plugin.painting.core.record :as record]
   [top.kzre.krro.plugin.painting.core.algo.anchor]
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
  (realize [this record _ctx]
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
      (undo/record-canvas-edited! canvas-id ))))
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
  (realize [this record _ctx]
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
      (undo/record-canvas-edited! canvas-id ))))

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
  (realize [this record _ctx]
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
      (undo/record-canvas-edited! canvas-id ))))

;; ═══════════════════════════════════════════════
;; VectorPathAdded —— 单个路径新增
;; path 自带 :id
;; ═══════════════════════════════════════════════


(defrecord VectorPathUpdated
  [layer-id path-id path dirty-tiles dirty-transform]
  AutoCloseable
  (close [_] nil)

  diff/IChange
  (seeds [_] layer-id)
  (empty-change? [_] false)
  (combine [this other] (composite/composite-change this other))

  proto/IOperation
  (realize [this record _ctx]
    (let [{:keys [canvas-id layer layers]} (record/layer-context record layer-id)
          dirty-paths (pv/paths layer)
          new-layer (pv/save-path layer path-id path)]
      (if-let [old-path (get dirty-paths path-id)]
        [(apply-layer-change record new-layer layers)
         [[:render-canvas canvas-id this]
          [:anchor-quadtree/delete-path canvas-id path-id old-path]
          [:anchor-quadtree/insert-path canvas-id path-id path]]]

        [(apply-layer-change record new-layer layers)
         [[:render-canvas canvas-id this]
          [:anchor-quadtree/insert-path canvas-id path-id path]]])))

  (record! [_ record]
    (let [{:keys [canvas-id]} record]
      (undo/record-canvas-edited! canvas-id))))

(defrecord VectorPathAdded
  [layer-id path-id path dirty-tiles dirty-transform]
  AutoCloseable
  (close [_] nil)

  diff/IChange
  (seeds [_] layer-id)
  (empty-change? [_] false)
  (combine [this other] (composite/composite-change this other))

  proto/IOperation
  (realize [this record _ctx]
    (let [{:keys [canvas-id layer layers]} (record/layer-context record layer-id)
          new-layer (pv/add-path layer path-id path)]
      [(apply-layer-change record new-layer layers)
       [[:render-canvas canvas-id this]
        [:anchor-quadtree/insert-path canvas-id path-id path]]]))

  (record! [_ record]
    (let [{:keys [canvas-id]} record]
      (undo/record-canvas-edited! canvas-id))))

;; ═══════════════════════════════════════════════
;; VectorPathRemoved —— 单个路径删除
;; 被删路径已不在 new-paths 里——只能携带 path-id
;; ═══════════════════════════════════════════════

(defrecord VectorPathRemoved
  [layer-id path-id dirty-tiles dirty-transform]
  AutoCloseable
  (close [_] nil)

  diff/IChange
  (seeds [_] layer-id)
  (empty-change? [_] false)
  (combine [this other] (composite/composite-change this other))

  proto/IOperation
  (realize [this record _ctx]
    (let [{:keys [canvas-id layer layers]} (record/layer-context record layer-id)
          dirty-paths (pv/paths layer)
          new-layer (pv/remove-path layer path-id)]
      [(apply-layer-change record new-layer layers)
       [[:render-canvas canvas-id this]
        [:anchor-quadtree/delete-path canvas-id path-id (get dirty-paths path-id)]]]))

  (record! [_ record]
    (let [{:keys [canvas-id]} record]
      (undo/record-canvas-edited! canvas-id ))))


;; ═══════════════════════════════════════════════
;; VectorPathOrderChanged —— 路径顺序变化
;; paths map 不变，只有 path-order 变
;; ═══════════════════════════════════════════════

(defrecord VectorPathOrderChanged
  [layer-id old-order new-order
   dirty-tiles dirty-transform]
  AutoCloseable
  (close [_] nil)

  diff/IChange
  (seeds [_] layer-id)
  (empty-change? [_] (= old-order new-order))
  (combine [this other] (composite/composite-change this other))

  proto/IOperation
  (realize [this record _ctx]
    (let [{:keys [canvas-id]} record
          {:keys [layer layers]} (record/layer-context record layer-id)
          new-layer (assoc layer :path-order new-order)]
      [(apply-layer-change record new-layer layers)
       [[:render-canvas canvas-id this]]]))
  ;; quadtree 无操作——锚点集合和坐标都没变

  (record! [_ record]
    (let [{:keys [canvas-id]} record]
      (undo/record-canvas-edited! canvas-id ))))

;; ═══════════════════════════════════════════════
;; VectorLayerPathAttrDirty —— 路径属性变化
;; 样式、闭合状态等所有非几何属性
;; ═══════════════════════════════════════════════

(defrecord VectorLayerPathAttrChanged
  [layer-id old-paths new-paths path-id
   dirty-tiles dirty-transform]
  AutoCloseable
  (close [_] nil)

  diff/IChange
  (seeds [_] layer-id)
  (empty-change? [_] (= old-paths new-paths))
  (combine [this other] (composite/composite-change this other))

  proto/IOperation
  (realize [this record _ctx]
    (let [{:keys [canvas-id]} record
          {:keys [layer layers]} (record/layer-context record layer-id)
          new-layer (pv/assoc-paths layer new-paths)]
      [(apply-layer-change record new-layer layers)
       [[:render-canvas canvas-id this]]]))
  ;; quadtree 无操作——锚点位置和拓扑都没变

  (record! [_ record]
    (let [{:keys [canvas-id]} record]
      (undo/record-canvas-edited! canvas-id ))))

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
  (realize [this record _ctx]
    (let [{:keys [canvas-id layer layers]} (record/layer-context record layer-id)
          new-layer  (pv/assoc-paths layer new-paths)
          new-layers (util/replace-layer new-layer layers)]
      [(assoc-in record [:canvas-data :layers] new-layers)
       [[:render-canvas canvas-id this]
        [:anchor-quadtree/rebuild canvas-id layer-id new-paths]]]))

  (record! [_ record]
    (let [{:keys [canvas-id]} record]
      (undo/record-canvas-edited! canvas-id ))))



(defn make-vector-anchors-edited
  [layer-id old-paths new-paths anchors dirty-tiles dirty-transform]
  (->VectorAnchorsEdited layer-id old-paths new-paths anchors
                         dirty-tiles dirty-transform))

(defn make-vector-anchor-inserted
  [layer-id old-paths new-paths anchor dirty-tiles dirty-transform]
  (->VectorAnchorInserted layer-id old-paths new-paths anchor
                          dirty-tiles dirty-transform))

(defn make-vector-anchor-deleted
  [layer-id old-paths new-paths anchor dirty-tiles dirty-transform]
  (->VectorAnchorDeleted layer-id old-paths new-paths anchor
                         dirty-tiles dirty-transform))

(defn make-vector-path-added
  [layer-id path-id path dirty-transform]
  (->VectorPathAdded layer-id path-id path (pv/path-tiles path) dirty-transform))

(defn make-vector-path-removed
  [layer-id path-id path dirty-transform]
  (->VectorPathRemoved layer-id path-id (pv/path-tiles path) dirty-transform))

(defn make-vector-path-updated
  [layer-id path-id old-path new-path dirty-transform]
  {:pre [(some? new-path)]}
  (->VectorPathUpdated layer-id path-id new-path
                       (if old-path
                         (into (pv/path-tiles new-path)
                               (pv/path-tiles old-path))
                         (pv/path-tiles new-path))
                       dirty-transform))

(defn make-vector-path-order-changed
  [layer-id old-order new-order dirty-tiles dirty-transform]
  (->VectorPathOrderChanged layer-id old-order new-order
                            dirty-tiles dirty-transform))

(defn make-vector-layer-path-attr-changed
  [layer-id old-paths new-paths path-id dirty-tiles dirty-transform]
  (->VectorLayerPathAttrChanged layer-id old-paths new-paths path-id
                                dirty-tiles dirty-transform))

(defn make-vector-layer-paths-dirty
  [layer-id old-paths new-paths dirty-tiles dirty-transform]
  (->VectorLayerPathsDirty layer-id old-paths new-paths
                           dirty-tiles dirty-transform))
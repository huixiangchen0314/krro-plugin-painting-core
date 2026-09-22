(ns top.kzre.krro.plugin.painting.core.changes.path
  "路径关注点的变更。

   四个 change——按 (数据形态, quadtree 副作用) 二元组划分：

     VectorAnchorPositionsChanged   paths-changed + anchors   update-anchors
     VectorPathsGeometryChanged     saved + deleted           delete/insert 或 rebuild
     VectorPathsAttrChanged         paths-changed             无
     VectorPathOrderChanged         new-order                 无

   VectorPathsGeometryChanged 采用 save 语义——saved / deleted 两个集合
   表达增删改——realize 从 record 的 old-paths 区分 added / updated。

   手柄 / 连续性 / 宽度的编辑语义在各自的关注点——本 ns 只承载
   它们的数据结构变化。"
  (:require
    [top.kzre.krro.canvas.core.layer.util :as util]
    [top.kzre.krro.canvas.vector.core :as cv]
    [top.kzre.krro.core.util.diff :as diff]
    [top.kzre.krro.plugin.painting.core.algo.anchor]
    [top.kzre.krro.plugin.painting.core.changes.composite :as composite]
    [top.kzre.krro.plugin.painting.core.oplog.protocol :as proto]
    [top.kzre.krro.plugin.painting.core.project.vector-layer :as pv]
    [top.kzre.krro.plugin.painting.core.record :as record]
    [top.kzre.krro.plugin.painting.core.undo.core :as undo])
  (:import
    (java.lang AutoCloseable)))

(defrecord VectorAnchorPositionsChanged
  [layer-id
   paths-changed       ; {path-id new-path} —— 只有变化的 path
   anchors             ; [Anchor ...] —— 变化的锚点集合（自带 :path-id / :point-idx）
   dirty-tiles dirty-transform]
  AutoCloseable
  (close [_] nil)

  diff/IChange
  (seeds [_] layer-id)
  (empty-change? [_] (empty? paths-changed))
  (combine [this other] (composite/composite-change this other))

  proto/IOperation
  (realize [this record _ctx]
    (let [{:keys [canvas-id layer layers]} (record/layer-context record layer-id)
          old-paths (pv/paths layer)
          new-paths (merge old-paths paths-changed)
          new-layer (assoc layer :paths new-paths)
          by-path   (group-by :path-id anchors)
          quadtree-fx
          (mapv (fn [[path-id path-anchors]]
                  [:anchor-quadtree/update-anchors canvas-id path-id
                   (get old-paths path-id) (get new-paths path-id) path-anchors])
                by-path)]
      [(assoc-in record [:canvas-data :layers]
                 (util/replace-layer new-layer layers))
       (into [[:render-canvas canvas-id this]] quadtree-fx)]))

  (record! [_ record]
    (let [{:keys [canvas-id]} record]
      (undo/record-canvas-edited! canvas-id))))


(defrecord VectorPathsGeometryChanged
  [layer-id
   saved    ; {path-id new-path} —— 新增或更新（save 语义——不区分）
   deleted  ; #{path-id}          —— 删除的 path-id（旧数据 realize 时从 record 取）
   dirty-tiles dirty-transform
   full?]   ; true = 全量替换——quadtree 走 rebuild
  AutoCloseable
  (close [_] nil)

  diff/IChange
  (seeds [_] layer-id)
  (empty-change? [_] (and (empty? saved) (empty? deleted)))
  (combine [this other] (composite/composite-change this other))

  proto/IOperation
  (realize [this record _ctx]
    (let [{:keys [canvas-id layer layers]} (record/layer-context record layer-id)
          old-paths         (cv/paths layer)
          ;; ─── 走库 API——维护 :path-order ───
          layer-after-del   (reduce cv/delete-path layer deleted)
          new-layer         (reduce (fn [l [id path]]
                                      (cv/save-path l id path))
                                    layer-after-del
                                    saved)
          new-paths         (cv/paths new-layer)
          quadtree-fx
          (if full?
            ;; 全量——重建整棵树
            [[:anchor-quadtree/rebuild canvas-id layer-id new-paths]]
            ;; 差量——三路分派
            (let [added-ids   (into #{} (remove #(contains? old-paths %)) (keys saved))
                  updated-ids (into #{} (filter #(contains? old-paths %)) (keys saved))]
              (concat
                ;; added —— 插入新子树
                (mapv (fn [id]
                        [:anchor-quadtree/insert-path canvas-id id (get saved id)])
                      added-ids)
                ;; deleted —— 删旧子树（old 从 record 取）
                (mapv (fn [id]
                        [:anchor-quadtree/delete-path canvas-id id (get old-paths id)])
                      deleted)
                ;; updated —— 删旧 + 插新
                (mapcat (fn [id]
                          [[:anchor-quadtree/delete-path canvas-id id (get old-paths id)]
                           [:anchor-quadtree/insert-path canvas-id id (get saved id)]])
                        updated-ids))))]
      [(assoc-in record [:canvas-data :layers]
                 (util/replace-layer new-layer layers))
       (into [[:render-canvas canvas-id this]] quadtree-fx)]))

  (record! [_ record]
    (let [{:keys [canvas-id]} record]
      (undo/record-canvas-edited! canvas-id))))

(defrecord VectorPathOrderChanged
  [layer-id new-order dirty-tiles dirty-transform]
  AutoCloseable
  (close [_] nil)

  diff/IChange
  (seeds [_] layer-id)
  (empty-change? [_] false)
  (combine [this other] (composite/composite-change this other))

  proto/IOperation
  (realize [this record _ctx]
    (let [{:keys [canvas-id layer layers]} (record/layer-context record layer-id)
          new-layer (assoc layer :path-order new-order)]
      [(assoc-in record [:canvas-data :layers]
                 (util/replace-layer new-layer layers))
       [[:render-canvas canvas-id this]]]))
  ;; quadtree 无操作——锚点集合和坐标都没变

  (record! [_ record]
    (let [{:keys [canvas-id]} record]
      (undo/record-canvas-edited! canvas-id))))

(defrecord VectorPathsAttrChanged
  [layer-id
   paths-changed       ; {path-id new-path} —— 只有变化的 path
   ;   空 vector 表示 path 级变化（样式 / 闭合）
   ;   非空表示锚点级变化（手柄 / 连续性）
   dirty-tiles dirty-transform]
  AutoCloseable
  (close [_] nil)

  diff/IChange
  (seeds [_] layer-id)
  (empty-change? [_] (empty? paths-changed))
  (combine [this other] (composite/composite-change this other))

  proto/IOperation
  (realize [this record _ctx]
    (let [{:keys [canvas-id layer layers]} (record/layer-context record layer-id)
          old-paths (pv/paths layer)
          new-paths (merge old-paths paths-changed)
          new-layer (assoc layer :paths new-paths)]
      [(assoc-in record [:canvas-data :layers]
                 (util/replace-layer new-layer layers))
       [[:render-canvas canvas-id this]]]))
  ;; quadtree 无操作——非几何变化不影响锚点位置

  (record! [_ record]
    (let [{:keys [canvas-id]} record]
      (undo/record-canvas-edited! canvas-id))))


(defn make-vector-anchor-positions-changed
  "锚点位置变化。
   old-paths —— 旧 paths（算旧位置瓦片）
   new-paths —— 新 paths（算新位置瓦片）
   anchors   —— [Anchor ...]，变化的锚点集合
   脏瓦片 —— 每个锚点旧位置 ∪ 新位置两侧段的并集"
  [layer-id old-paths new-paths anchors dirty-transform]
  (let [changed-ids   (into #{} (map :path-id anchors))
        paths-changed (select-keys new-paths changed-ids)
        dirty-tiles   (into #{}
                            (mapcat (fn [a]
                                      (into (or (pv/anchor-tiles old-paths a) #{})
                                            (or (pv/anchor-tiles new-paths a) #{}))))
                            anchors)]
    (->VectorAnchorPositionsChanged layer-id paths-changed anchors
                                    dirty-tiles dirty-transform)))


(defn make-vector-paths-geometry-changed
  "跨路径原子几何变化（save 语义——不区分新增和更新）。

   old-paths —— 完整旧 paths map（用于算 deleted 的脏瓦片）。
   :saved    —— {path-id new-path} 新增或更新的 path。
   :deleted  —— #{path-id} 删除的 path-id（旧数据从 old-paths 取）。

   脏瓦片：saved 的新瓦片 ∪ deleted 的旧瓦片。"
  [layer-id old-paths dirty-transform
   & {:keys [saved deleted full?]
      :or   {saved {} deleted #{}
             full? false}}]
  (let [saved-tiles   (into #{} (mapcat pv/path-tiles (vals saved)))
        deleted-tiles (into #{} (mapcat (fn [id]
                                          (pv/path-tiles (get old-paths id)))
                                        deleted))
        dirty-tiles   (into saved-tiles deleted-tiles)]
    (->VectorPathsGeometryChanged layer-id saved deleted
                                  dirty-tiles dirty-transform full?)))

(defn make-vector-path-order-changed
  [layer-id new-order dirty-tiles dirty-transform]
  (->VectorPathOrderChanged layer-id new-order
                            dirty-tiles dirty-transform))

(defn make-vector-anchors-attr-changed
  "锚点级非几何属性变化（手柄 / 连续性等）。
   old-paths —— 旧 paths（算旧段瓦片）
   new-paths —— 新 paths（算新段瓦片 + 提取 paths-changed）
   anchors   —— [Anchor ...]，属性变化的锚点集合
   脏瓦片 —— 每个变化锚点两侧段的旧 ∪ 新。"
  [layer-id old-paths new-paths anchors dirty-transform]
  (let [changed-ids   (into #{} (map :path-id anchors))
        paths-changed (select-keys new-paths changed-ids)
        dirty-tiles   (into #{}
                            (mapcat (fn [a]
                                      (into (or (pv/anchor-tiles old-paths a) #{})
                                            (or (pv/anchor-tiles new-paths a) #{}))))
                            anchors)]
    (->VectorPathsAttrChanged layer-id paths-changed
                             dirty-tiles dirty-transform)))

(defn make-vector-path-attr-changed
  "path 级非几何属性变化（样式 / 闭合等）。
   old-path —— 旧 path（算旧瓦片）
   new-path —— 新 path（算新瓦片）
   脏瓦片 —— 整条 path 的旧 ∪ 新（属性变化可能影响整条渲染）。"
  [layer-id path-id old-path new-path dirty-transform]
  {:pre [(some? new-path)]}
  (let [dirty-tiles (if old-path
                      (into (pv/path-tiles new-path)
                            (pv/path-tiles old-path))
                      (pv/path-tiles new-path))]
    (->VectorPathsAttrChanged layer-id {path-id new-path}
                             dirty-tiles dirty-transform)))

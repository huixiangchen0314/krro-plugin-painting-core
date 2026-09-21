(ns top.kzre.krro.plugin.painting.core.changes.path
  "路径领域的变更对象。

    处理**路径自身关注点**的变化——位置、拓扑、结构、顺序，
    以及**非几何属性**（样式 / 闭合 / 手柄 / 连续性等）的变化。

    手柄与连续性虽然数据形态相似（都改手柄向量），但都是
    **路径的非几何属性**——它们不影响锚点在 quadtree 中的位置——
    因此和样式 / 闭合归为同一个 change 类型：`VectorPathAttrChanged`。

    宽度是**独立关注点**——数据形态是 `width-samples` / `t-params`——
    在 `changes/width` ns。

    ## 变更清单（10 个）

    集合不变 · 几何（影响锚点位置——quadtree 必须更新）：
      VectorAnchorPositionsChanged —— 锚点位置变化（细粒度 quadtree）
      VectorPathGeometryChanged    —— 单 path 几何变化（通用）
      VectorAnchorInserted         —— 锚点插入（拓扑 +）
      VectorAnchorDeleted          —— 锚点删除（拓扑 -）
      VectorPathsGeometryChanged   —— 跨路径原子几何变化

    集合不变 · 非几何属性（quadtree 不动）：
      VectorPathAttrChanged        —— 样式 / 闭合 / 手柄 / 连续性等
                                      （只影响渲染，不影响锚点位置）

    集合不变 · 顺序（quadtree 不动）：
      VectorPathOrderChanged       —— path-order 变化

    集合变化 · 单 path：
      VectorPathAdded              —— 单 path 新增
      VectorPathRemoved            —— 单 path 删除

    全量（唯一例外）：
      VectorLayerPathsDirty        —— 全量替换（回滚 / 导入）

    ## 分类判据

    几何 vs 属性——按「是否影响锚点在 quadtree 中的位置」分：
      几何 —— 锚点位置 / 拓扑变 → quadtree 必须更新
      属性 —— 锚点位置 / 拓扑不变，只影响渲染 → quadtree 不动

    手柄 / 连续性属于属性——它们改的是手柄向量或约束字段——
    锚点位置 `:x :y` 不变——quadtree 无操作。

    ## 设计原则

    - **change 只携带 record 里取不到的变化数据**——旧数据 realize 时从 record 取；
      唯一例外是 VectorLayerPathsDirty（全量语义需要 old + new）。
    - **change 名描述数据形态变化**——不写「谁被编辑了」，写「什么数据变了」。
    - **脏瓦片在 make- 构造器内计算**——调用方只提供领域语义。
    - **脏瓦片 = 旧 ∪ 新**——不脏画面的唯一保证。
    - **位置 / 手柄 / 连续性用 anchor-tiles（锚点两侧段）；
      path 级属性用 path-tiles（整条 path）**——按变化范围选。
    - **容器参数在前，领域对象在后**——`[layer-id paths... anchor ... dirty-transform]`。
    - **Anchor 携带 path-id 时不重复字段**。

    ## 双维度正交

    每个 change 在 (数据形态, quadtree 副作用) 二元组上唯一——没有可合并的。
    这保证了缓存失效的精确性——change 类型的粒度 = 缓存失效的粒度。
    "
  (:require
    [top.kzre.krro.canvas.core.layer.util :as util]
    [top.kzre.krro.canvas.vector.core :as canvas.vector]
    [top.kzre.krro.core.util.diff :as diff]
    [top.kzre.krro.plugin.painting.core.algo.anchor]
    [top.kzre.krro.plugin.painting.core.changes.composite :as composite]
    [top.kzre.krro.plugin.painting.core.oplog.protocol :as proto]
    [top.kzre.krro.plugin.painting.core.project.vector-layer :as pv]
    [top.kzre.krro.plugin.painting.core.record :as record]
    [top.kzre.krro.plugin.painting.core.undo.core :as undo])
  (:import
    (java.lang AutoCloseable)
    (top.kzre.krro.canvas.vector.anchor Anchor)))

;; ═══════════════════════════════════════════════
;; VectorAnchorPositionsChanged —— 锚点位置变化（拓扑不变）
;; 只携带变化的 path —— {path-id new-path}
;; 旧 path realize 时从 record 取
;; ═══════════════════════════════════════════════

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

;; ═══════════════════════════════════════════════
;; VectorAnchorInserted —— 锚点插入（中间插入 / 端点挤出）
;; anchor 自带 :path-id / :point-idx
;; 消费端根据 anchor 的 point-idx 区分：
;;   - 0 或末尾   → 端点挤出
;;   - 中间        → 中间插入
;; ═══════════════════════════════════════════════

(defrecord VectorAnchorInserted
  [layer-id
   path                ; 新 path（含新锚点）
   ^Anchor anchor      ; 自带 :path-id / :point-idx
   dirty-tiles dirty-transform]
  AutoCloseable
  (close [_] nil)

  diff/IChange
  (seeds [_] layer-id)
  (empty-change? [_] false)
  (combine [this other] (composite/composite-change this other))

  proto/IOperation
  (realize [this record _ctx]
    (let [{:keys [canvas-id layer layers]} (record/layer-context record layer-id)
          path-id   (:path-id anchor)
          old-path  (get (pv/paths layer) path-id)
          new-layer (canvas.vector/save-path layer path-id path)]
      [(assoc-in record [:canvas-data :layers]
                 (util/replace-layer new-layer layers))
       [[:render-canvas canvas-id this]
        [:anchor-quadtree/delete-path canvas-id path-id old-path]
        [:anchor-quadtree/insert-path canvas-id path-id path]]]))

  (record! [_ record]
    (let [{:keys [canvas-id]} record]
      (undo/record-canvas-edited! canvas-id))))

;; ═══════════════════════════════════════════════
;; VectorAnchorDeleted —— 删除锚点
;; anchor 自带 :path-id
;; ═══════════════════════════════════════════════

(defrecord VectorAnchorDeleted
  [layer-id
   path                ; 新 path（不含被删锚点）
   ^Anchor anchor      ; 自带 :path-id / :point-idx
   dirty-tiles dirty-transform]
  AutoCloseable
  (close [_] nil)

  diff/IChange
  (seeds [_] layer-id)
  (empty-change? [_] false)
  (combine [this other] (composite/composite-change this other))

  proto/IOperation
  (realize [this record _ctx]
    (let [{:keys [canvas-id layer layers]} (record/layer-context record layer-id)
          path-id   (:path-id anchor)
          old-path  (get (pv/paths layer) path-id)
          new-layer (canvas.vector/save-path layer path-id path)]
      [(assoc-in record [:canvas-data :layers]
                 (util/replace-layer new-layer layers))
       [[:render-canvas canvas-id this]
        [:anchor-quadtree/delete-path canvas-id path-id old-path]
        [:anchor-quadtree/insert-path canvas-id path-id path]]]))

  (record! [_ record]
    (let [{:keys [canvas-id]} record]
      (undo/record-canvas-edited! canvas-id))))

;; ═══════════════════════════════════════════════
;; VectorPathGeometryChanged —— 单 path 几何变化（通用）
;; 携带 path-id + 新 path；旧 path realize 时从 record 取
;; 涵盖：reverse / reform / fit-segment 等
;; ═══════════════════════════════════════════════

(defrecord VectorPathGeometryChanged
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
          old-path  (get (pv/paths layer) path-id)
          new-layer (canvas.vector/save-path layer path-id path)]
      [(assoc-in record [:canvas-data :layers]
                 (util/replace-layer new-layer layers))
       [[:render-canvas canvas-id this]
        [:anchor-quadtree/delete-path canvas-id path-id old-path]
        [:anchor-quadtree/insert-path canvas-id path-id path]]]))

  (record! [_ record]
    (let [{:keys [canvas-id]} record]
      (undo/record-canvas-edited! canvas-id))))

;; ═══════════════════════════════════════════════
;; VectorPathAdded —— 单 path 新增
;; ═══════════════════════════════════════════════

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
          new-layer (canvas.vector/save-path layer path-id path)]
      [(assoc-in record [:canvas-data :layers]
                 (util/replace-layer new-layer layers))
       [[:render-canvas canvas-id this]
        [:anchor-quadtree/insert-path canvas-id path-id path]]]))

  (record! [_ record]
    (let [{:keys [canvas-id]} record]
      (undo/record-canvas-edited! canvas-id))))

;; ═══════════════════════════════════════════════
;; VectorPathRemoved —— 单 path 删除
;; 被删 path 已不在 record 里——只能携带 path-id
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
          old-path  (get (pv/paths layer) path-id)
          new-layer (canvas.vector/delete-path layer path-id)]
      [(assoc-in record [:canvas-data :layers]
                 (util/replace-layer new-layer layers))
       [[:render-canvas canvas-id this]
        [:anchor-quadtree/delete-path canvas-id path-id old-path]]]))

  (record! [_ record]
    (let [{:keys [canvas-id]} record]
      (undo/record-canvas-edited! canvas-id))))

;; ═══════════════════════════════════════════════
;; VectorPathsGeometryChanged —— 跨路径几何原子变化
;; added   —— {path-id new-path}
;; removed —— #{path-id}（旧数据 realize 时从 record 取）
;; updated —— {path-id new-path}
;; ═══════════════════════════════════════════════

(defrecord VectorPathsGeometryChanged
  [layer-id
   added               ; {path-id new-path}
   removed             ; #{path-id}
   updated             ; {path-id new-path}
   dirty-tiles dirty-transform]
  AutoCloseable
  (close [_] nil)

  diff/IChange
  (seeds [_] layer-id)
  (empty-change? [_]
    (and (empty? added) (empty? removed) (empty? updated)))
  (combine [this other] (composite/composite-change this other))

  proto/IOperation
  (realize [this record _ctx]
    (let [{:keys [canvas-id layer layers]} (record/layer-context record layer-id)
          old-paths (pv/paths layer)
          new-paths (-> old-paths
                        (apply dissoc removed)
                        (merge added updated))
          new-layer (assoc layer :paths new-paths)
          quadtree-fx
          (concat
            ;; added —— 插入新子树
            (mapv (fn [[id path]]
                    [:anchor-quadtree/insert-path canvas-id id path])
                  added)
            ;; removed —— 删旧子树（old 从 record 取）
            (mapv (fn [id]
                    [:anchor-quadtree/delete-path canvas-id id (get old-paths id)])
                  removed)
            ;; updated —— 删旧 + 插新
            (mapcat (fn [[id path]]
                      [[:anchor-quadtree/delete-path canvas-id id (get old-paths id)]
                       [:anchor-quadtree/insert-path canvas-id id path]])
                    updated))]
      [(assoc-in record [:canvas-data :layers]
                 (util/replace-layer new-layer layers))
       (into [[:render-canvas canvas-id this]] quadtree-fx)]))

  (record! [_ record]
    (let [{:keys [canvas-id]} record]
      (undo/record-canvas-edited! canvas-id))))

;; ═══════════════════════════════════════════════
;; VectorPathOrderChanged —— 路径顺序变化
;; 只携带 new-order；旧 order 从 record 取
;; ═══════════════════════════════════════════════

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

;; ═══════════════════════════════════════════════
;; VectorPathAttrChanged —— 路径非几何属性变化
;; 涵盖：样式 / 闭合 / 手柄 / 连续性等
;; 数据形态：paths-changed + anchors（anchors 为上下文——空表示 path 级变化）
;; quadtree 无操作——锚点位置不变
;; ═══════════════════════════════════════════════

(defrecord VectorPathsAttrChanged
  [layer-id
   paths-changed       ; {path-id new-path} —— 只有变化的 path
   anchors             ; [Anchor ...] —— 变化的锚点集合；
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

;; ═══════════════════════════════════════════════
;; VectorLayerPathsDirty —— 全量替换（回滚 / 导入）
;; 唯一携带 old + new 全量的 change —— 兜底语义
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
          new-layer  (assoc layer :paths new-paths)]
      [(assoc-in record [:canvas-data :layers]
                 (util/replace-layer new-layer layers))
       [[:render-canvas canvas-id this]
        [:anchor-quadtree/rebuild canvas-id layer-id new-paths]]]))

  (record! [_ record]
    (let [{:keys [canvas-id]} record]
      (undo/record-canvas-edited! canvas-id))))

;; ═══════════════════════════════════════════════
;; make- 构造器
;; ═══════════════════════════════════════════════

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

(defn make-vector-anchor-inserted
  "锚点插入。
   old-paths —— 旧 paths（用于算脏瓦片）
   new-paths —— 新 paths（含新锚点）
   anchor    —— 新插入的锚点（自带 :path-id / :point-idx）
   脏瓦片 —— 新锚点两侧段 ∪ 旧整条 path 瓦片"
  [layer-id old-paths new-paths ^Anchor anchor dirty-transform]
  (let [path-id     (:path-id anchor)
        new-path    (get new-paths path-id)
        old-path    (get old-paths path-id)
        new-tiles   (or (pv/anchor-tiles new-paths anchor) #{})
        old-tiles   (if old-path (pv/path-tiles old-path) #{})
        dirty-tiles (into new-tiles old-tiles)]
    (->VectorAnchorInserted layer-id new-path anchor
                            dirty-tiles dirty-transform)))

(defn make-vector-anchor-deleted
  "锚点删除。
   old-paths —— 旧 paths（含被删锚点）
   new-paths —— 新 paths
   anchor    —— 被删的锚点（自带 :path-id / :point-idx）
   脏瓦片 —— 被删锚点原两侧段 ∪ 新整条 path 瓦片"
  [layer-id old-paths new-paths ^Anchor anchor dirty-transform]
  (let [path-id     (:path-id anchor)
        new-path    (get new-paths path-id)
        old-tiles   (or (pv/anchor-tiles old-paths anchor) #{})
        new-tiles   (if new-path (pv/path-tiles new-path) #{})
        dirty-tiles (into old-tiles new-tiles)]
    (->VectorAnchorDeleted layer-id new-path anchor
                           dirty-tiles dirty-transform)))

(defn make-vector-path-added
  [layer-id path-id path dirty-transform]
  (->VectorPathAdded layer-id path-id path
                     (pv/path-tiles path)
                     dirty-transform))

(defn make-vector-path-removed
  [layer-id path-id path dirty-transform]
  (->VectorPathRemoved layer-id path-id
                       (pv/path-tiles path)
                       dirty-transform))

(defn make-vector-path-geometry-changed
  "单 path 更新。
   old-path —— 用于算脏瓦片；不存入 record。
   new-path —— 存入 record。"
  [layer-id path-id old-path new-path dirty-transform]
  {:pre [(some? new-path)]}
  (let [dirty-tiles (if old-path
                      (into (pv/path-tiles new-path)
                            (pv/path-tiles old-path))
                      (pv/path-tiles new-path))]
    (->VectorPathGeometryChanged layer-id path-id new-path
                                 dirty-tiles dirty-transform)))

(defn make-vector-paths-geometry-changed
  "跨路径原子变更。

   added   —— {path-id new-path} 新增路径
   removed —— {path-id old-path} 被删除的路径（含数据，仅用于算脏瓦片——
                                   存入 record 时只保留 path-id）
   updated —— {path-id new-path} 更新的路径

   脏瓦片在构造器内计算——按 added / removed / updated 三路取并集。"
  [layer-id dirty-transform
   & {:keys [added removed updated]
      :or   {added {} removed {} updated {}}}]
  (let [added-tiles   (into #{} (mapcat pv/path-tiles (vals added)))
        removed-tiles (into #{} (mapcat pv/path-tiles (vals removed)))
        updated-tiles (into #{} (mapcat pv/path-tiles (vals updated)))
        dirty-tiles   (into added-tiles (concat removed-tiles updated-tiles))]
    (->VectorPathsGeometryChanged
      layer-id
      added                    ; {id new-path}
      (set (keys removed))     ; #{id} —— 只保留 id
      updated                  ; {id new-path}
      dirty-tiles
      dirty-transform)))

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
    (->VectorPathsAttrChanged layer-id paths-changed anchors
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
    (->VectorPathsAttrChanged layer-id {path-id new-path} []
                             dirty-tiles dirty-transform)))

(defn make-vector-layer-paths-dirty
  [layer-id old-paths new-paths dirty-tiles dirty-transform]
  (->VectorLayerPathsDirty layer-id old-paths new-paths
                           dirty-tiles dirty-transform))
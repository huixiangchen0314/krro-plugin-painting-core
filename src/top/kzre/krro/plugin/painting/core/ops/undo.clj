(ns top.kzre.krro.plugin.painting.core.ops.undo
  "撤销系统：记录与恢复图层操作及光栅笔触。
   全部光栅数据现在基于 tiled-canvas。笔触仅保存脏区域，恢复时只写回脏区域。"
  (:require
    [taoensso.timbre :as log]
    [top.kzre.krro.canvas.core.layer.core :as layer-core]
    [top.kzre.krro.core.frame :as frame]
    [top.kzre.krro.core.hook :as hook]
    [top.kzre.krro.core.message :as msg]
    [top.kzre.krro.plugin.painting.core.ops.layer :as layer]
    [top.kzre.krro.plugin.painting.core.ops.snapshot :as snap]
    [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
    [top.kzre.krro.plugin.painting.core.project.raster-layer :as pr]
    [top.kzre.krro.plugin.painting.core.spec :as spec]
    [top.kzre.krro.plugin.undo.core :as undo]
    [top.kzre.krro.plugin.undo.protocol :as undo-p]
    [top.kzre.krro.util.tiled-canvas :as tcanvas])
  (:import
    (top.kzre.krro.util TiledCanvasUtils)))

;; ── 撤销类型定义 ──────────────────────────────────
(defonce undo-type-layer-render-attrs-changed ::layer-render-attrs-changed)
(defonce undo-type-layer-changed ::layer-changed)
(defonce undo-type-raster-stroke ::raster-stroke)
(defonce undo-type-raster-layer-add ::raster-layer-add)
(defonce undo-type-raster-layer-remove ::raster-layer-remove)
(defonce undo-type-raster-layer-replace ::raster-layer-replace)

;; ── 序列号管理 ────────────────────────────────────
(def undo-metadata-seq-key ::canvas-raster-state-seq)

(defn inc-undo-metadata-seq-key []
  (let [f       frame/*current-frame*
        old-seq (or (frame/param f undo-metadata-seq-key) 0)
        new-seq (inc old-seq)]
    (frame/set-param! f undo-metadata-seq-key new-seq)
    old-seq))

;; ═══════════════════════════════════════════════════════
;; 辅助：从画布中提取脏 tile 子集，构造迷你画布
;; ═══════════════════════════════════════════════════════
(defn- sub-canvas-for-dirties
  "提取 dirties 集合中的 tile，返回仅包含这些 tile 的 mini canvas map。"
  [canvas dirties]
  (let [tiles (select-keys (:tiles canvas) dirties)
        tile-keys (keys tiles)
        min-tx (if (seq tile-keys)
                 (apply min (map #(TiledCanvasUtils/unpackTx %) tile-keys))
                 (:min-tx canvas))
        max-tx (if (seq tile-keys)
                 (apply max (map #(TiledCanvasUtils/unpackTx %) tile-keys))
                 (:max-tx canvas))
        min-ty (if (seq tile-keys)
                 (apply min (map #(TiledCanvasUtils/unpackTy %) tile-keys))
                 (:min-ty canvas))
        max-ty (if (seq tile-keys)
                 (apply max (map #(TiledCanvasUtils/unpackTy %) tile-keys))
                 (:max-ty canvas))]
    {:min-tx   min-tx
     :max-tx   max-tx
     :min-ty   min-ty
     :max-ty   max-ty
     :tile-size (:tile-size canvas)
     :tiles     tiles}))

;; ═══════════════════════════════════════════════════════
;; 笔触元数据工厂
;; ═══════════════════════════════════════════════════════
(defn make-raster-stroke-meta
  "构造光栅笔触撤销元数据，仅包含脏 tile 的新旧快照。"
  [canvas-id layer-id old-canvas new-canvas dirties]
  (let [old-sub (sub-canvas-for-dirties old-canvas dirties)
        new-sub (sub-canvas-for-dirties new-canvas dirties)]
    {:type          undo-type-raster-stroke
     :seq           (inc-undo-metadata-seq-key)
     :canvas-id     canvas-id
     :layer-id      layer-id
     :old-snapshot  (snap/wrap-tiled-canvas old-sub)
     :new-snapshot  (snap/wrap-tiled-canvas new-sub)}))

;; ═══════════════════════════════════════════════════════
;; 笔触记录与恢复
;; ═══════════════════════════════════════════════════════
(defn record-raster-stroke!
  "记录一次光栅笔触的脏区域快照，以便撤销/重做。
   参数：
     canvas-id, layer-id
     old-canvas : 绘制前的完整画布
     new-canvas : 绘制后的完整画布
     dirties    : 脏 tile 键集合"
  [canvas-id layer-id old-canvas new-canvas dirties]
  (log/debug "Recording tiled raster stroke undo...")
  (try
    (let [meta (make-raster-stroke-meta canvas-id layer-id old-canvas new-canvas dirties)]
      (undo/record-state! meta)
      (log/info "Tiled raster undo state recorded [seq:" (:seq meta) "]"))
    (catch Exception e
      (log/error e "Failed to record tiled raster stroke undo."))))

(defn- restore-tiled-stroke!
  "将快照中的子画布合并回当前图层画布。"
  [meta snapshot-key]
  (let [canvas-id  (:canvas-id meta)
        layer-id   (:layer-id meta)
        wrap       (snapshot-key meta)   ;; :old-snapshot 或 :new-snapshot
        sub-canvas (snap/read-tiled-canvas wrap)
        cd         (pc/canvas-data! canvas-id)
        layer      (layer-core/find-layer layer-id (:layers cd))]
    (if-not layer
      (msg/error (str "Layer not found for restore: " layer-id))
      (let [current-canvas (:canvas layer)
            new-canvas     (tcanvas/copy-to! current-canvas sub-canvas)
            new-layer      (assoc layer :canvas new-canvas)
            path           (layer-core/find-layer-path layer-id (:layers cd))]
        (layer/update-layer-at! canvas-id path (fn [_] new-layer))
        (log/debug "Tiled stroke restored from" snapshot-key)))))

;; ═══════════════════════════════════════════════════════
;; 图层添加/移除/替换元数据工厂
;; ═══════════════════════════════════════════════════════
(defn make-raster-layer-add-meta [canvas-id path layer snapshot-wrapper]
  {:type          undo-type-raster-layer-add
   :seq           (inc-undo-metadata-seq-key)
   :canvas-id     canvas-id
   :layer-id      (:id layer)
   :path          path
   :layer         (pc/persistable-layer layer)
   :snapshot      snapshot-wrapper})

(defn make-raster-layer-remove-meta [canvas-id path layer snapshot-wrapper]
  {:type          undo-type-raster-layer-remove
   :seq           (inc-undo-metadata-seq-key)
   :canvas-id     canvas-id
   :layer-id      (:id layer)
   :path          path
   :layer         (pc/persistable-layer layer)
   :snapshot      snapshot-wrapper})

(defn make-raster-layer-replace-meta [canvas-id path old-layer new-layer old-wrapper new-wrapper]
  {:type          undo-type-raster-layer-replace
   :seq           (inc-undo-metadata-seq-key)
   :canvas-id     canvas-id
   :path          path
   :old-layer     (pc/persistable-layer old-layer)
   :new-layer     (pc/persistable-layer new-layer)
   :old-snapshot  old-wrapper
   :new-snapshot  new-wrapper})

(defn make-layer-changed-meta [canvas-id]
  {:type              undo-type-layer-changed
   :seq               (inc-undo-metadata-seq-key)
   :canvas-id         canvas-id})

(defn make-layer-render-attrs-changed-meta [canvas-id]
  {:type              undo-type-layer-render-attrs-changed
   :seq               (inc-undo-metadata-seq-key)
   :canvas-id         canvas-id})

;; ═══════════════════════════════════════════════════════
;; 记录函数
;; ═══════════════════════════════════════════════════════
(defn record-state! [canvas-id]
  (undo/record-state! (make-layer-changed-meta canvas-id)))

(defn record-layer-render-attrs-state! [canvas-id]
  (undo/record-state! (make-layer-render-attrs-changed-meta canvas-id)))

(defn record-raster-layer-add!
  [canvas-id path layer]
  (let [canvas (:canvas layer)
        wrap   (snap/wrap-tiled-canvas canvas)]
    (undo/record-state! (make-raster-layer-add-meta canvas-id path layer wrap))))

(defn record-raster-layer-remove!
  [canvas-id path removed]
  (let [canvas (:canvas removed)
        wrap   (snap/wrap-tiled-canvas canvas)]
    (undo/record-state! (make-raster-layer-remove-meta canvas-id path removed wrap))))

(defn record-raster-layer-replace!
  [canvas-id path old-layer new-layer]
  (let [old-canvas (:canvas old-layer)
        new-canvas (:canvas new-layer)
        same?      (identical? old-canvas new-canvas)
        old-wrap   (when-not same? (snap/wrap-tiled-canvas old-canvas))
        new-wrap   (when-not same? (snap/wrap-tiled-canvas new-canvas))]
    (undo/record-state! (make-raster-layer-replace-meta canvas-id path old-layer new-layer old-wrap new-wrap))))

;; ═══════════════════════════════════════════════════════
;; 恢复多方法分派
;; ═══════════════════════════════════════════════════════
(defmulti restore-canvas-state!
          (fn [lifecycle meta] [lifecycle (:type meta)]))

;; ── 图层添加 ────────────────────────────────────
(defmethod restore-canvas-state! [:after-undo undo-type-raster-layer-add] [_ meta]
  (pr/delete-raster! (:layer-id meta))
  (hook/run-hook! spec/layer-changed-hook-key (:canvas-id meta)))

(defmethod restore-canvas-state! [:before-redo undo-type-raster-layer-add] [_ meta]
  (let [canvas (snap/read-tiled-canvas (:snapshot meta))]
    (pr/create-raster! (:layer-id meta) (:canvas-id meta) canvas)))

(defmethod restore-canvas-state! [:after-redo undo-type-raster-layer-add] [_ meta]
  (hook/run-hook! spec/layer-changed-hook-key (:canvas-id meta)))

;; ── 图层删除 ────────────────────────────────────
(defmethod restore-canvas-state! [:before-undo undo-type-raster-layer-remove] [_ meta]
  (let [canvas (snap/read-tiled-canvas (:snapshot meta))]
    (pr/create-raster! (:layer-id meta) (:canvas-id meta) canvas)))

(defmethod restore-canvas-state! [:after-undo undo-type-raster-layer-remove] [_ meta]
  (layer/refresh-canvas-and-layer! (:canvas-id meta)))

(defmethod restore-canvas-state! [:after-redo undo-type-raster-layer-remove] [_ meta]
  (pr/delete-raster! (:layer-id meta))
  (layer/refresh-canvas-and-layer! (:canvas-id meta)))

;; ── 图层替换 ────────────────────────────────────
(defmethod restore-canvas-state! [:before-undo undo-type-raster-layer-replace] [_ meta]
  (when-let [old-wrap (:old-snapshot meta)]
    (let [canvas (snap/read-tiled-canvas old-wrap)]
      (pr/create-raster! (:id (:old-layer meta)) (:canvas-id meta) canvas))))

(defmethod restore-canvas-state! [:after-undo undo-type-raster-layer-replace] [_ meta]
  (layer/refresh-canvas-and-layer! (:canvas-id meta)))

(defmethod restore-canvas-state! [:before-redo undo-type-raster-layer-replace] [_ meta]
  (when-let [new-wrap (:new-snapshot meta)]
    (let [canvas (snap/read-tiled-canvas new-wrap)]
      (pr/create-raster! (:id (:new-layer meta)) (:canvas-id meta) canvas))))

(defmethod restore-canvas-state! [:after-redo undo-type-raster-layer-replace] [_ meta]
  (layer/refresh-canvas-and-layer! (:canvas-id meta)))

;; ── 光栅笔触 ────────────────────────────────────
(defmethod restore-canvas-state! [:after-undo undo-type-raster-stroke] [_ meta]
  (restore-tiled-stroke! meta :old-snapshot))

(defmethod restore-canvas-state! [:after-redo undo-type-raster-stroke] [_ meta]
  (restore-tiled-stroke! meta :new-snapshot))

;; ── 其他图层状态变更 ────────────────────────────
(defmethod restore-canvas-state! [:after-undo undo-type-layer-changed] [_ metadata]
  (hook/run-hook! spec/layer-changed-hook-key (:canvas-id metadata)))

(defmethod restore-canvas-state! [:after-redo undo-type-layer-changed] [_ metadata]
  (hook/run-hook! spec/layer-changed-hook-key (:canvas-id metadata)))

(defmethod restore-canvas-state! [:after-undo undo-type-layer-render-attrs-changed] [_ metadata]
  (layer/refresh-canvas-and-layer! (:canvas-id metadata)))

(defmethod restore-canvas-state! [:after-redo undo-type-layer-render-attrs-changed] [_ metadata]
  (layer/refresh-canvas-and-layer! (:canvas-id metadata)))

(defmethod restore-canvas-state! :default [_lifecycle _meta])

;; ═══════════════════════════════════════════════════════
;; 钩子处理器
;; ═══════════════════════════════════════════════════════
(defn- make-undo-handler [lifecycle]
  (fn [event]
    (let [node (:old-node event)
          meta (when node (undo-p/metadata node))]
      (when meta
        (restore-canvas-state! lifecycle meta)))))

(defn- make-redo-handler [lifecycle]
  (fn [event]
    (let [node (:new-node event)
          meta (when node (undo-p/metadata node))]
      (when meta
        (restore-canvas-state! lifecycle meta)))))

(defn init-undo-hooks! []
  (log/info "Initializing undo/redo hooks...")
  (let [after-undo-handler (make-undo-handler :after-undo)
        before-undo-handler (make-undo-handler :before-undo)
        before-redo-handler (make-redo-handler :before-redo)
        after-redo-handler (make-redo-handler :after-redo)]
    (hook/add-hook! :krro.undo/before-undo-hook before-undo-handler)
    (hook/add-hook! :krro.undo/after-undo-hook after-undo-handler)
    (hook/add-hook! :krro.undo/before-redo-hook before-redo-handler)
    (hook/add-hook! :krro.undo/after-redo-hook after-redo-handler)
    (fn []
      (log/debug "Removing undo/redo hooks.")
      (hook/remove-hook! :krro.undo/before-undo-hook before-undo-handler)
      (hook/remove-hook! :krro.undo/after-undo-hook after-undo-handler)
      (hook/remove-hook! :krro.undo/before-redo-hook before-redo-handler)
      (hook/remove-hook! :krro.undo/after-redo-hook after-redo-handler))))
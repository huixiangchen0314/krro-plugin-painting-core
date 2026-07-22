(ns top.kzre.krro.plugin.painting.core.ops.undo
  "撤销系统：记录与恢复图层操作及光栅笔触。
   全部光栅数据现在基于 TiledCanvas。笔触仅保存脏区域，恢复时只写回脏区域。
   所有恢复操作均强制全图刷新。"
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
    [top.kzre.krro.plugin.painting.core.state :as state])
  (:import
    (java.util.function Consumer)
    (top.kzre.krro.util.tile TiledCanvas)
    (java.util HashMap)))

;; 图层渲染属性更新
(defonce undo-type-layer-render-attrs-changed ::layer-render-attrs-changed)
;; 图层编辑属性更新
(defonce undo-type-layer-edit-attrs-changed ::layer-edit-attrs-changed)

;; 光栅图层操作类型
(defonce undo-type-raster-stroke ::raster-stroke)
(defonce undo-type-raster-layer-add ::raster-layer-add)
(defonce undo-type-raster-layer-remove ::raster-layer-remove)

;; ── 序列号管理 ────────────────────────────────────
(def undo-metadata-seq-key ::canvas-raster-state-seq)

(defn inc-undo-metadata-seq-key []
  (let [f       frame/*current-frame*
        old-seq (or (frame/param f undo-metadata-seq-key) 0)
        new-seq (inc old-seq)]
    (frame/set-param! f undo-metadata-seq-key new-seq)
    old-seq))

;; ═══════════════════════════════════════════════════════
;; 笔触元数据工厂
;; ═══════════════════════════════════════════════════════
(defn make-raster-stroke-meta
  "构造光栅笔触撤销元数据，现保存完整画布快照（全量）。"
  [canvas-id layer-id ^TiledCanvas old-canvas ^TiledCanvas new-canvas dirties]
  {:type          undo-type-raster-stroke
   :seq           (inc-undo-metadata-seq-key)
   :canvas-id     canvas-id
   :layer-id      layer-id
   :dirties       (set dirties)
   :old-snapshot  (snap/wrap-tiled-canvas old-canvas dirties)
   :new-snapshot  (snap/wrap-tiled-canvas new-canvas dirties)})

;; ═══════════════════════════════════════════════════════
;; 笔触记录与恢复
;; ═══════════════════════════════════════════════════════
(defn- restore-tiled-stroke!
  "从快照恢复光栅笔触。将脏瓦片合并到当前画布中，并强制刷新。"
  [meta snapshot-key]
  (let [canvas-id   (:canvas-id meta)
        layer-id    (:layer-id meta)
        dirties     (:dirties meta)
        wrap        (snapshot-key meta)
        ^TiledCanvas dirty-canvas (snap/read-tiled-canvas wrap)
        cd          (pc/canvas-data! canvas-id)
        layer       (layer-core/find-layer layer-id (:layers cd))]
    (if-not layer
      (msg/error (str "Layer not found for restore: " layer-id))
      (let [^TiledCanvas current-canvas (:canvas layer)]
        (.deleteTiles current-canvas dirties)
        (.mergeCanvas current-canvas dirty-canvas)
        ;; 关键：标记画布脏并强制刷新 UI
        (state/invalidate-canvas-dirty! canvas-id)
        (layer/refresh-canvas-and-layer! canvas-id)
        (log/debug "Tiled stroke restored from" snapshot-key)))
    (.clear dirty-canvas)))

;; ═══════════════════════════════════════════════════════
;; 图层添加/移除元数据工厂
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

(defn make-layer-edit-attrs-changed-meta [canvas-id]
  {:type              undo-type-layer-edit-attrs-changed
   :seq               (inc-undo-metadata-seq-key)
   :canvas-id         canvas-id})

(defn make-layer-render-attrs-changed-meta [canvas-id]
  {:type              undo-type-layer-render-attrs-changed
   :seq               (inc-undo-metadata-seq-key)
   :canvas-id         canvas-id})

;; ═══════════════════════════════════════════════════════
;; 记录函数
;; ═══════════════════════════════════════════════════════
(defn record-layer-edit-attrs-state! [canvas-id]
  (undo/record-state! (make-layer-edit-attrs-changed-meta canvas-id)))

(defn record-layer-render-attrs-state! [canvas-id]
  (undo/record-state! (make-layer-render-attrs-changed-meta canvas-id)))

(defn record-raster-layer-add!
  [canvas-id path layer]
  (let [^TiledCanvas canvas (:canvas layer)
        wrap   (snap/wrap-tiled-canvas canvas)]
    (undo/record-state! (make-raster-layer-add-meta canvas-id path layer wrap))))

(defn record-raster-layer-remove!
  [canvas-id path removed]
  (let [^TiledCanvas canvas (:canvas removed)
        wrap   (snap/wrap-tiled-canvas canvas)]
    (undo/record-state! (make-raster-layer-remove-meta canvas-id path removed wrap))))

(defn record-raster-stroke!
  [canvas-id layer-id ^TiledCanvas old-canvas ^TiledCanvas new-canvas dirties]
  (log/debug "Recording tiled raster stroke undo...")
  (try
    (let [meta (make-raster-stroke-meta canvas-id layer-id old-canvas new-canvas dirties)]
      (undo/record-state! meta)
      (log/info "Tiled raster undo state recorded [seq:" (:seq meta) "]"))
    (catch Exception e
      (log/error e "Failed to record tiled raster stroke undo."))))

;; ═══════════════════════════════════════════════════════
;; 恢复多方法分派
;; ═══════════════════════════════════════════════════════
(defmulti restore-canvas-state!
          (fn [lifecycle meta] [lifecycle (:type meta)]))

;; ── 图层添加 ────────────────────────────────────
(defmethod restore-canvas-state! [:after-undo undo-type-raster-layer-add] [_ meta]
  (pr/delete-raster! (:layer-id meta))
  (let [canvas-id (:canvas-id meta)]
    (state/invalidate-canvas-dirty! canvas-id)
    (layer/refresh-canvas-and-layer! canvas-id)))

(defmethod restore-canvas-state! [:before-redo undo-type-raster-layer-add] [_ meta]
  (let [canvas (snap/read-tiled-canvas (:snapshot meta))]
    (pr/create-raster* (:layer-id meta) (:canvas-id meta) canvas)))

(defmethod restore-canvas-state! [:after-redo undo-type-raster-layer-add] [_ meta]
  (let [canvas-id (:canvas-id meta)]
    (state/invalidate-canvas-dirty! canvas-id)
    (layer/refresh-canvas-and-layer! canvas-id)))

;; ── 图层删除 ────────────────────────────────────
(defmethod restore-canvas-state! [:before-undo undo-type-raster-layer-remove] [_ meta]
  (let [canvas (snap/read-tiled-canvas (:snapshot meta))]
    (pr/create-raster* (:layer-id meta) (:canvas-id meta) canvas)))

(defmethod restore-canvas-state! [:after-undo undo-type-raster-layer-remove] [_ meta]
  (let [canvas-id (:canvas-id meta)]
    (state/invalidate-canvas-dirty! canvas-id)
    (layer/refresh-canvas-and-layer! canvas-id)))

(defmethod restore-canvas-state! [:after-redo undo-type-raster-layer-remove] [_ meta]
  (pr/delete-raster! (:layer-id meta))
  (let [canvas-id (:canvas-id meta)]
    (state/invalidate-canvas-dirty! canvas-id)
    (layer/refresh-canvas-and-layer! canvas-id)))

;; ── 光栅笔触 ────────────────────────────────────
(defmethod restore-canvas-state! [:after-undo undo-type-raster-stroke] [_ meta]
  (restore-tiled-stroke! meta :old-snapshot))

(defmethod restore-canvas-state! [:after-redo undo-type-raster-stroke] [_ meta]
  (restore-tiled-stroke! meta :new-snapshot))

;; ── 其他图层状态变更 ────────────────────────────
(defmethod restore-canvas-state! [:after-undo undo-type-layer-edit-attrs-changed] [_ metadata]
  (let [canvas-id (:canvas-id metadata)]
    (state/invalidate-canvas-dirty! canvas-id)
    (hook/run-hook! spec/layer-changed-hook-key canvas-id )))

(defmethod restore-canvas-state! [:after-redo undo-type-layer-edit-attrs-changed] [_ metadata]
  (let [canvas-id (:canvas-id metadata)]
    (state/invalidate-canvas-dirty! canvas-id)
    (hook/run-hook! spec/layer-changed-hook-key canvas-id )))

(defmethod restore-canvas-state! [:after-undo undo-type-layer-render-attrs-changed] [_ metadata]
  (let [canvas-id (:canvas-id metadata)]
    (state/invalidate-canvas-dirty! canvas-id)
    (layer/refresh-canvas-and-layer! canvas-id)))

(defmethod restore-canvas-state! [:after-redo undo-type-layer-render-attrs-changed] [_ metadata]
  (let [canvas-id (:canvas-id metadata)]
    (state/invalidate-canvas-dirty! canvas-id)
    (layer/refresh-canvas-and-layer! canvas-id)))

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
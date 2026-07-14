(ns top.kzre.krro.plugin.painting.canvas.undo
  "撤销系统：记录与恢复图层操作及光栅笔触。
   图层属性通过 project 的多方法 persistable-layer / restore-layer! 处理，
   像素数据使用 snapshot 模块的混合存储策略（小尺寸留内存，大尺寸写临时文件）。"
  (:require
   [taoensso.timbre :as log]
   [top.kzre.krro.canvas.core.canvas.protocol :as cp]
   [top.kzre.krro.canvas.core.layer.core :as layer-core]
   [top.kzre.krro.canvas.core.obb :as obb]
   [top.kzre.krro.core.frame :as frame]
   [top.kzre.krro.core.hook :as hook]
   [top.kzre.krro.core.message :as msg]
   [top.kzre.krro.plugin.painting.canvas.layer :as layer]
   [top.kzre.krro.plugin.painting.canvas.snapshot :as snap]
   [top.kzre.krro.plugin.painting.project.canvas :as pc]
   [top.kzre.krro.plugin.painting.project.raster-layer :as pr]
   [top.kzre.krro.plugin.painting.spec :as spec]
   [top.kzre.krro.plugin.undo.core :as undo]
   [top.kzre.krro.plugin.undo.protocol :as undo-p])
  (:import
   (java.io FileNotFoundException)
   (top.kzre.krro.canvas.core Arrays)))


(defonce undo-type-layer-render-attrs-changed ::layer-render-attrs-changed)
(defonce undo-type-layer-changed ::layer-changed)
(defonce undo-type-raster-stroke ::raster-stroke)
(defonce undo-type-raster-layer-add ::raster-layer-add)
(defonce undo-type-raster-layer-remove ::raster-layer-remove)
(defonce undo-type-raster-layer-replace ::raster-layer-replace)

;; ── 序列号管理 ───────────────────────────────
(def undo-metadata-seq-key ::canvas-raster-state-seq)

(defn inc-undo-metadata-seq-key []
  (let [f       frame/*current-frame*
        old-seq (or (frame/param f undo-metadata-seq-key) 0)
        new-seq (inc old-seq)]
    (frame/set-param! f undo-metadata-seq-key new-seq)
    old-seq))

;; ── 元数据工厂（委托给 persistable-layer）─────────
(defn make-raster-stroke-meta [canvas-id layer-id obb old-wrapper new-wrapper]
  {:type              undo-type-raster-stroke
   :seq               (inc-undo-metadata-seq-key)
   :canvas-id         canvas-id
   :layer-id          layer-id
   :obb               obb
   :old-snapshot      old-wrapper
   :new-snapshot      new-wrapper})

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

;; TODO 细化改变类型能用于后续性能优化
(defn make-layer-render-attrs-changed-meta [canvas-id]
  {:type              undo-type-layer-render-attrs-changed
   :seq               (inc-undo-metadata-seq-key)
   :canvas-id         canvas-id})


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

;; ── 记录函数 ─────────────────────────────────
(defn record-raster-stroke!
  [canvas-id layer-id old-pixels new-pixels dirty-rects]
  (log/debug "Recording raster undo state...")
  (try
    (let [cd         (pc/canvas-data! canvas-id)
          width      (:width cd)
          height     (:height cd)
          obb-desc   (obb/rects->obb dirty-rects)
          old-snap   (obb/save-obb-snapshot old-pixels width height obb-desc)
          new-snap   (obb/save-obb-snapshot new-pixels width height obb-desc)
          old-w      (snap/wrap-snapshot! old-snap)
          new-w      (snap/wrap-snapshot! new-snap)
          meta       (make-raster-stroke-meta canvas-id layer-id obb-desc old-w new-w)]
      (undo/record-state! meta)
      (log/info "Raster undo state recorded [seq:" (:seq meta) "]"))
    (catch Exception e
      (log/error e "Failed to record raster undo state."))))

(defn record-state! [canvas-id]
  (undo/record-state! (make-layer-changed-meta canvas-id)))

(defn record-layer-render-attrs-state! [canvas-id]
  (undo/record-state! (make-layer-render-attrs-changed-meta canvas-id)))

(defn record-raster-layer-replace!
  [canvas-id path old-layer new-layer]
  (let [old-pixels (cp/data (:canvas old-layer))
        new-pixels (cp/data (:canvas new-layer))
        same?      (identical? old-pixels new-pixels)
        old-wrap   (when-not same? (snap/wrap-pixels! old-pixels))
        new-wrap   (when-not same? (snap/wrap-pixels! new-pixels))
        meta       (make-raster-layer-replace-meta canvas-id path old-layer new-layer old-wrap new-wrap)]
    (undo/record-state! meta)
    (log/info "Raster layer replace recorded [seq:" (:seq meta) "]"
              (when same? "(pixels unchanged)"))))

(defn record-raster-layer-add!
  [canvas-id path layer]
  (let [pixels (cp/data (:canvas layer))
        wrapper (snap/wrap-pixels! pixels)
        meta (make-raster-layer-add-meta canvas-id path layer wrapper)]
    (undo/record-state! meta)))

(defn record-raster-layer-remove!
  [canvas-id path removed]
  (let [pixels (cp/data (:canvas removed))
        wrapper (snap/wrap-pixels! pixels)
        meta (make-raster-layer-remove-meta canvas-id path removed wrapper)]
    (undo/record-state! meta)))

;; ── 恢复函数 ─────────────────────────────────
(defn restore-raster-state!
  "恢复光栅笔触快照。"
  [meta snapshot-key]
  (let [seq-num   (:seq meta "?")
        canvas-id (:canvas-id meta)
        layer-id  (:layer-id meta)
        cd        (pc/canvas-data! canvas-id)
        w         (:width cd)
        h         (:height cd)
        layer-buf (layer/raster-layer-buffer canvas-id layer-id)
        layer     (layer-core/find-layer layer-id (:layers cd))]
    (log/info "Restoring raster state [seq:" seq-num "] from" snapshot-key)
    (if-not layer-buf
      (msg/error (str "Layer buffer does not exist: layer" layer-id))
      (try
        (let [obb    (:obb meta)
              snap-w (snapshot-key meta)
              snap   (snap/read-snapshot! snap-w)]
          (obb/restore-obb-snapshot layer-buf w h obb snap)
          (let [canvas  (:canvas layer)
                dest    (cp/data canvas)]
            (when (not= layer-buf dest) (Arrays/copy layer-buf dest))
            (layer/refresh-canvas-frames! canvas-id))
          (log/debug "Raster state restored successfully [seq:" seq-num "]"))
        (catch FileNotFoundException e
          (log/error e "Snapshot file not found [seq:" seq-num "]"))
        (catch Exception e
          (log/error e "Failed to restore raster state [seq:" seq-num "]"))))))

;; ── 多方法分派恢复 ────────────────────────────
(defmulti restore-canvas-state!
          (fn [lifycycle meta] [lifycycle (:type meta)]))

(defmethod restore-canvas-state! [:after-undo undo-type-raster-layer-add] [_ meta]
  (let [canvas-id (:canvas-id meta)
        layer-id (:layer-id meta)]
    (pr/delete-raster! layer-id)
    (hook/run-hook! spec/layer-changed-hook-key canvas-id)))

(defmethod restore-canvas-state! [:before-redo undo-type-raster-layer-add] [_ meta]
  (let [canvas-id (:canvas-id meta)
        layer-id (:layer-id meta)
        buffer   (snap/read-pixels! (:snapshot meta))]
    (pr/create-raster* layer-id canvas-id buffer)))

(defmethod restore-canvas-state! [:after-redo undo-type-raster-layer-add] [_ meta]
  (let [canvas-id  (:canvas-id meta)]
    (hook/run-hook! spec/layer-changed-hook-key canvas-id)))

(defmethod restore-canvas-state! [:before-undo undo-type-raster-layer-remove] [_ meta]
  (let [canvas-id (:canvas-id meta)
        layer-id (:layer-id meta)
        pixels   (snap/read-pixels! (:snapshot meta))]
    (pr/create-raster* layer-id canvas-id pixels)
    ))

(defmethod restore-canvas-state! [:after-undo undo-type-raster-layer-remove] [_ meta]
  (let [canvas-id  (:canvas-id meta)]
    (layer/refresh-canvas-and-layer! canvas-id)))

(defmethod restore-canvas-state! [:after-redo undo-type-raster-layer-remove] [_ meta]
  (let [canvas-id (:canvas-id meta)
        layer-id (:layer-id meta)]
    (pr/delete-raster! layer-id)
    (layer/refresh-canvas-and-layer! canvas-id)))

(defmethod restore-canvas-state! [:after-undo undo-type-raster-stroke] [_ meta]
  (restore-raster-state! meta :old-snapshot))

(defmethod restore-canvas-state! [:after-redo undo-type-raster-stroke] [_ meta]
  (restore-raster-state! meta :new-snapshot))

(defmethod restore-canvas-state! [:after-undo undo-type-layer-changed] [_ metadata]
  (hook/run-hook! spec/layer-changed-hook-key (:canvas-id metadata)))

(defmethod restore-canvas-state! [:after-redo undo-type-layer-changed] [_ metadata]
  (hook/run-hook! spec/layer-changed-hook-key (:canvas-id metadata)))

(defmethod restore-canvas-state! [:after-undo undo-type-layer-render-attrs-changed] [_ metadata]
  (layer/refresh-canvas-and-layer! (:canvas-id metadata)))

(defmethod restore-canvas-state! [:after-redo undo-type-layer-render-attrs-changed] [_ metadata]
  (layer/refresh-canvas-and-layer! (:canvas-id metadata)))

(defmethod restore-canvas-state! [:before-undo undo-type-raster-layer-replace] [_ meta]
  (let [old-snap (when-let [w (:old-snapshot meta)] (snap/read-pixels! w))]
    (when old-snap
      (pr/create-raster* (:id (:old-layer meta)) (:canvas-id meta) old-snap))))

(defmethod restore-canvas-state! [:after-undo undo-type-raster-layer-replace] [_ meta]
  (layer/refresh-canvas-and-layer! (:canvas-id meta)))

(defmethod restore-canvas-state! [:before-redo undo-type-raster-layer-replace] [_ meta]
  (let [new-snap (when-let [w (:new-snapshot meta)] (snap/read-pixels! w))]
    (when new-snap
      (pr/create-raster* (:id (:new-layer meta)) (:canvas-id meta) new-snap))))

(defmethod restore-canvas-state! [:after-redo undo-type-raster-layer-replace] [_ meta]
  (layer/refresh-canvas-and-layer! (:canvas-id meta)))

(defmethod restore-canvas-state! :default [_lifecycle _meta])

;; ── 钩子处理器 ───────────────────────────────
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
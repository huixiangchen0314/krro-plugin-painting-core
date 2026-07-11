(ns top.kzre.krro.plugin.painting.canvas.undo
  "撤销系统：记录与恢复图层操作及光栅笔触。
   图层属性通过 project 的多方法 persistable-layer / restore-layer! 处理，
   像素数据独立于侧表，存入临时文件。"
  (:require
    [clojure.java.io :as io]
    [taoensso.timbre :as log]
    [top.kzre.krro.canvas.core.canvas.protocol :as cp]
    [top.kzre.krro.canvas.core.layer.core :as layer-core]
    [top.kzre.krro.canvas.core.obb :as obb]
    [top.kzre.krro.core.frame :as frame]
    [top.kzre.krro.core.hook :as hook]
    [top.kzre.krro.core.message :as msg]
    [top.kzre.krro.plugin.painting.canvas.layer :as layer]
    [top.kzre.krro.plugin.painting.canvas.project :as proj]
    [top.kzre.krro.plugin.painting.spec :as spec]
    [top.kzre.krro.plugin.undo.core :as undo]
    [top.kzre.krro.plugin.undo.protocol :as undo-p])
  (:import
    (java.io File FileNotFoundException)
    (top.kzre.krro.canvas.core Arrays)))

;; 通用图层更新
(defonce undo-type-layer-changed ::layer-changed)
(defonce undo-type-raster-stroke ::raster-stroke)
(defonce undo-type-raster-layer-add ::raster-layer-add)
(defonce undo-type-raster-layer-remove ::raster-layer-remove)


;; ── 序列号管理 ───────────────────────────────
(def undo-metadata-seq-key ::canvas-raster-state-seq)

(defn inc-undo-metadata-seq-key []
  (let [f       frame/*current-frame*
        old-seq (or (frame/param f undo-metadata-seq-key) 0)
        new-seq (inc old-seq)]
    (frame/set-param! f undo-metadata-seq-key new-seq)
    old-seq))

;; ── 元数据工厂（委托给 persistable-layer）─────────
(defn make-raster-stroke-meta [canvas-id layer-id obb old-file new-file]
  {:type              undo-type-raster-stroke
   :seq               (inc-undo-metadata-seq-key)
   :canvas-id         canvas-id
   :layer-id          layer-id
   :obb               obb
   :old-snapshot-file old-file
   :new-snapshot-file new-file})

(defn make-layer-changed-meta [canvas-id]
  {:type         undo-type-layer-changed
   :seq               (inc-undo-metadata-seq-key)
   :canvas-id         canvas-id})

(defn make-raster-layer-add-meta [canvas-id path layer snapshot-file]
  {:type     undo-type-raster-layer-add
   :seq           (inc-undo-metadata-seq-key)
   :canvas-id     canvas-id
   :path          path
   :layer   (proj/persistable-layer layer)   ;; 多方法
   :snapshot-file snapshot-file})

(defn make-raster-layer-remove-meta [canvas-id path layer snapshot-file]
  {:type          undo-type-raster-layer-remove
   :seq           (inc-undo-metadata-seq-key)
   :canvas-id     canvas-id
   :path          path
   :layer         (proj/persistable-layer layer)
   :snapshot-file snapshot-file})

(defn record-raster-stroke!
  [canvas-id layer-id old-pixels new-pixels dirty-rects]
  (log/debug "Recording raster undo state...")
  (try
    (let [cd         (proj/canvas-data! canvas-id)
          width      (:width cd)
          height     (:height cd)
          obb-desc   (obb/rects->obb dirty-rects)
          old-snap (obb/save-obb-snapshot old-pixels width height obb-desc)
          new-snap (obb/save-obb-snapshot new-pixels width height obb-desc)
          old-file   (obb/write-snapshot-temp! old-snap)
          new-file   (obb/write-snapshot-temp! new-snap)
          meta       (make-raster-stroke-meta canvas-id layer-id obb-desc old-file new-file)]
      (undo/record-state! meta)
      (log/info "Raster undo state recorded [seq:" (:seq meta) "]"))
    (catch Exception e
      (log/error e "Failed to record raster undo state."))))

(defn record-state! [canvas-id]
  (undo/record-state! (make-layer-changed-meta canvas-id)))

(defn record-raster-layer-add!
  [canvas-id path layer]
  (let [pixels (cp/data (:canvas layer))
        file (Arrays/writeTemp pixels)
        meta (make-raster-layer-add-meta canvas-id path layer file)]
    (undo/record-state! meta)))

(defn record-raster-layer-remove!
  [canvas-id path removed]
  (let [pixels (layer/raster-layer-buffer canvas-id (:id removed))
        file (Arrays/writeTemp pixels)
        meta (make-raster-layer-remove-meta canvas-id path removed file)]
    (undo/record-state! meta)))

(defn restore-raster-state!
  "恢复光栅笔触快照。"
  [meta snapshot-key]
  (let [seq-num   (:seq meta "?")
        canvas-id (:canvas-id meta)
        layer-id  (:layer-id meta)
        cd        (proj/canvas-data! canvas-id)
        w         (:width cd)
        h         (:height cd)
        layer-buf (layer/raster-layer-buffer canvas-id layer-id)
        layer     (layer-core/find-layer layer-id (:layers cd))]
    (log/info "Restoring raster state [seq:" seq-num "] from" snapshot-key)
    (if-not layer
      (msg/error (str "Cannot restore [seq:" seq-num "]: layer" layer-id "not found."))
      (if-not layer-buf
        (msg/error (str "Layer buffer does not exist: layer" layer-id))
        (try
          (let [obb    (:obb meta)
                file   (snapshot-key meta)
                snap   (obb/read-snapshot-temp! file)]
            (obb/restore-obb-snapshot layer-buf w h obb snap)
            (let [canvas  (:canvas layer)
                  dest    (cp/data canvas)]
              (when (not= layer-buf dest) (Arrays/copy layer-buf dest))
              (layer/refresh-canvas-frames! canvas-id))
            (log/info "Raster state restored successfully [seq:" seq-num "]"))
          (catch FileNotFoundException e
            (log/error e "Snapshot file not found [seq:" seq-num "]"))
          (catch Exception e
            (log/error e "Failed to restore raster state [seq:" seq-num "]")))))))

;; ── 多方法分派恢复 ────────────────────────────
(defmulti restore-canvas-state!
          (fn [lifycycle meta] [lifycycle (:type meta)]))


(defmethod restore-canvas-state! [:after-undo undo-type-raster-layer-add] [_ meta]
  (let [canvas-id (:canvas-id meta)
        layer-id (:layer-id meta)]                          ;; 我们只管理保护键数据.
    (proj/delete-raster! layer-id)
    (hook/run-hook! spec/layer-changed-hook-key canvas-id)))

(defmethod restore-canvas-state! [:before-redo undo-type-raster-layer-add] [_ meta]
  (let [canvas-id (:canvas-id meta)
        layer-id (:layer-id meta)
        snapshot-file (:snapshot-file meta)
        buffer (Arrays/readTemp snapshot-file)]
    (proj/add-raster* layer-id canvas-id (:data buffer))))

(defmethod restore-canvas-state! [:after-redo undo-type-raster-layer-add] [_ meta]
  (let [canvas-id  (:canvas-id meta)]
    (hook/run-hook! spec/layer-changed-hook-key canvas-id)))



(defmethod restore-canvas-state! [:before-undo undo-type-raster-layer-remove] [_ meta]
  (let [canvas-id (:canvas-id meta)
        layer-id (:layer-id meta)
        snapshot-file (:snapshot-file meta)
        buffer (Arrays/readTemp snapshot-file)]
    (proj/add-raster* layer-id canvas-id (:data buffer))))

(defmethod restore-canvas-state! [:after-undo undo-type-raster-layer-remove] [_ meta]
  (let [canvas-id  (:canvas-id meta)]
    (hook/run-hook! spec/layer-changed-hook-key canvas-id)))

(defmethod restore-canvas-state! [:after-redo undo-type-raster-layer-remove] [_ meta]
  (let [canvas-id (:canvas-id meta)
        layer-id (:layer-id meta)]                          ;; 我们只管理保护键数据.
    (proj/delete-raster! layer-id)
    (hook/run-hook! spec/layer-changed-hook-key canvas-id)))

(defmethod restore-canvas-state! [:after-undo undo-type-raster-stroke] [_ meta]
  (restore-raster-state! meta :old-snapshot-file))

(defmethod restore-canvas-state! [:after-redo undo-type-raster-stroke] [_ meta]
  (restore-raster-state! meta :new-snapshot-file))

(defmethod restore-canvas-state! [:after-undo undo-type-layer-changed] [_ metadata]
 (hook/run-hook! spec/layer-changed-hook-key (:canvas-id metadata)))

(defmethod restore-canvas-state! [:after-redo undo-type-layer-changed] [_ metadata]
  (hook/run-hook! spec/layer-changed-hook-key (:canvas-id metadata)))

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
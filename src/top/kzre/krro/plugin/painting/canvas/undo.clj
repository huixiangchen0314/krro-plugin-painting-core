(ns top.kzre.krro.plugin.painting.canvas.undo
  (:require
    [clojure.java.io :as io]
    [taoensso.timbre :as log]
    [taoensso.tufte :as tufte :refer [p]]
    [top.kzre.krro.canvas.core.canvas.protocol :as cp]
    [top.kzre.krro.canvas.core.layer.core :as layer-core]
    [top.kzre.krro.canvas.core.obb :as obb]
    [top.kzre.krro.core.frame :as frame]
    [top.kzre.krro.core.hook :as hook]
    [top.kzre.krro.plugin.painting.canvas.layer :as layer]
    [top.kzre.krro.plugin.painting.canvas.project :as canv-proj]
    [top.kzre.krro.plugin.painting.canvas.state :as state]
    [top.kzre.krro.plugin.painting.spec :as spec]
    [top.kzre.krro.plugin.undo.core :as undo]
    [top.kzre.krro.plugin.undo.protocol :as undo-p]
    [top.kzre.krro.core.message :as msg])
  (:import
    (java.io File FileNotFoundException)
    (javafx.application Platform)
    (top.kzre.krro.canvas.core Arrays)
    (top.kzre.krro.plugin.painting.canvas.state CanvasRuntime)))

;; ── 序列号管理 ──────────────────────────────────────────────
(def canvas-raster-state-seq-key ::canvas-raster-state-seq)

(defn inc-canvas-raster-state-seq-key
  "递增并返回当前帧的画笔状态序列号（递增前的旧值）。"
  []
  (let [f       frame/*current-frame*
        old-seq (or (frame/param f canvas-raster-state-seq-key) 0)
        new-seq (inc old-seq)]
    (frame/set-param! f canvas-raster-state-seq-key new-seq)
    old-seq))

(defn canvas-raster-state-seq
  "返回当前帧的画笔状态序列号（最新已使用的序号）。"
  []
  (or (frame/param frame/*current-frame* canvas-raster-state-seq-key) 0))

;; ── 核心 undo/redo 功能 ──────────────────────────────────────
(defn record-raster-state!
  "为光栅图层的当前笔画创建 OBB 快照，并记录到 undo 系统。"
  [canvas-id layer-id old-pixels new-pixels dirty-rects]
  (log/debug "Recording raster undo state...")
  (try
    (let [cd         (canv-proj/canvas-data canvas-id)
          width      (.width cd)
          height     (.height cd)
          seq-num    (inc-canvas-raster-state-seq-key)
          obb        (obb/rects->obb dirty-rects)
          old-snap   (obb/save-obb-snapshot old-pixels width height obb)
          new-snap   (obb/save-obb-snapshot new-pixels width height obb)
          old-file   (File/createTempFile "krro-undo-old-" ".raw")
          new-file   (File/createTempFile "krro-undo-new-" ".raw")
          _          (with-open [out (io/output-stream old-file)]
                       (obb/write-snapshot! old-snap out))
          _          (with-open [out (io/output-stream new-file)]
                       (obb/write-snapshot! new-snap out))
          meta       {:krro.painting/undo-type :raster-stroke
                      :seq   seq-num
                      :obb   obb
                      :old-snapshot-file (.getPath old-file)
                      :new-snapshot-file (.getPath new-file)
                      :layer-id layer-id
                      :canvas-id canvas-id}]
      (undo/record-state meta)
      (log/info "Raster undo state recorded [seq:" seq-num "]"))
    (catch Exception e
      (log/error e "Failed to record raster undo state."))))

(defn restore-raster-state!
  "根据快照文件恢复光栅图层像素，并刷新画布。
   meta 中应包含 :seq :layer-id :obb 及快照文件路径。"
  [meta snapshot-file-key]
  (let [seq-num   (:seq meta "?")
        canvas-id (:canvas-id meta)
        layer-id  (:layer-id meta)
        runtime   (state/ensure-runtime! canvas-id)
        cd         (canv-proj/canvas-data canvas-id)
        w         (:width cd)
        h         (:height cd)
        layer-buf (state/layer-buffer runtime)
        layer     (layer-core/find-layer layer-id (:layers cd))]
    (log/info "Restoring raster state [seq:" seq-num "] from" snapshot-file-key
              "layer:" layer-id)
    (if-not layer
      (msg/error (str "Cannot restore [seq:" seq-num "]: layer" layer-id "not found."))
      (try
        (let [obb    (:obb meta)
              file   (snapshot-file-key meta)
              _      (log/debug "Reading snapshot [seq:" seq-num "] from" file)
              snap   (obb/read-snapshot! (io/input-stream (io/file file)))]
          (obb/restore-obb-snapshot layer-buf w h obb snap)
          (let [preview (state/preview-buffer runtime)
                canvas  (:canvas layer)
                dest    (cp/data canvas)]
            (Arrays/copy layer-buf dest)
            (layer/render-canvas-by-id! canvas-id preview)
            (hook/run-hook! spec/canvas-dirty-hook-key canvas-id))
          (log/info "Raster state restored successfully [seq:" seq-num "]"))
        (catch FileNotFoundException e
          (log/error e "Snapshot file not found [seq:" seq-num "]:"
                     (snapshot-file-key meta)))
        (catch Exception e
          (log/error e "Failed to restore raster state [seq:" seq-num "]"))))))

(defn- make-undo-handler []
  (fn [event]
    (let [node (:old-node event)
          meta (when node (undo-p/metadata node))]
      (log/debug "Undo triggered [seq:" (:seq meta "?") "] meta:" meta)
      (if (= :raster-stroke (:krro.painting/undo-type meta))
        (tufte/profile {:id :krro.painting/raster-stroke-undo
                        :dynamic? true}
                       (p :krro.painting/raster-stroke-undo
                          (restore-raster-state! meta :old-snapshot-file)))
        (log/debug "Undo event not a raster stroke, skipped.")))))

(defn- make-redo-handler []
  (fn [event]
    (let [node (:new-node event)
          meta (when node (undo-p/metadata node))]
      (log/debug "Redo triggered [seq:" (:seq meta "?") "] meta:" meta)
      (if (= :raster-stroke (:krro.painting/undo-type meta))
        (tufte/profile {:id :krro.painting/raster-stroke-redo
                        :dynamic? true}
                       (p :krro.painting/raster-stroke-redo
                          (restore-raster-state!  meta  :new-snapshot-file)))
        (log/debug "Redo event not a raster stroke, skipped.")))))

(defn init-undo-hooks! []
  (log/info "Initializing undo/redo hooks...")
  (let [undo-handler (make-undo-handler )
        redo-handler (make-redo-handler )]
    (hook/add-hook! :krro.undo/undo-hook undo-handler)
    (hook/add-hook! :krro.undo/redo-hook redo-handler)
    (fn []
      (log/info "Removing undo/redo hooks.")
      (hook/remove-hook! :krro.undo/undo-hook undo-handler)
      (hook/remove-hook! :krro.undo/redo-hook redo-handler))))
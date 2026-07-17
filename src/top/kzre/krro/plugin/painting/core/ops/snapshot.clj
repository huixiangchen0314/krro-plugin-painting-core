(ns top.kzre.krro.plugin.painting.core.ops.snapshot
  "快照存储策略：小快照留内存，大快照写临时文件，阈值可通过 defcustom 配置。
   提供两种快照类型的封装：
     - SnapshotData（OBB 快照，含宽高）
     - 纯 float[] 像素数组（用于图层添加/移除）"
  (:require
   [top.kzre.krro.util.tiled-canvas :as tcanvas]
   [top.kzre.krro.canvas.core.obb :as obb]
   [top.kzre.krro.core.custom :as custom])
  (:import
    (java.io
    BufferedInputStream
    BufferedOutputStream
    DataInputStream
    DataOutputStream
    File
    FileInputStream
    FileOutputStream)
    (java.nio.file Files Path)
    (top.kzre.krro.canvas.core Arrays)
    (top.kzre.krro.plugin.painting.core.ops TiledCanvasIOUtils)))

;; ═══════════════════════════════════════════════════════
;; 阈值配置
;; ═══════════════════════════════════════════════════════
(custom/defcustom ::memory-threshold
                  (* 4 1024 1024 1024 1024)   ; 4 MB 默认
                  :type :integer
                  :group :krro.painting/performance
                  :doc "快照保留在内存中的最大字节数（浮点数组长度×4）。超过此值将写入临时文件。")

;; ═══════════════════════════════════════════════════════
;; 内部工具
;; ═══════════════════════════════════════════════════════
(defn- snapshot-size [^floats data]
  (* (alength data) 4))

;; ═══════════════════════════════════════════════════════
;; OBB 快照（SnapshotData）封装
;; ═══════════════════════════════════════════════════════
(defn wrap-snapshot!
  "snap 为由 obb/save-obb-snapshot 返回的快照（含 width/height/data）。
 返回 {:type :memory/:file, :value <data-or-path>}"
  [snap]
  (let [data (:data snap)]
    (if (< (snapshot-size data) (custom/get-custom ::memory-threshold))
      {:type :memory
       :value snap}
      (let [path (obb/write-snapshot-temp! snap)]
        {:type :file
         :value path}))))

(defn read-snapshot!
  "从封装结构中读取 OBB 快照。"
  [{:keys [type value]}]
  (case type
    :memory value
    :file   (obb/read-snapshot-temp! value)))

(defn delete-snapshot!
  "若快照存在于临时文件，则删除该文件（用于清理）。"
  [{:keys [type value]}]
  (when (= type :file)
    (try
      (.delete (File. ^String  value))
      (catch Exception _))))

;; ═══════════════════════════════════════════════════════
;; 纯浮点像素数组封装（用于图层添加/移除）
;; ═══════════════════════════════════════════════════════
(defn wrap-pixels!
  "pixels 为 float 数组。返回 {:type :memory/:file, :value <array-or-path>}"
  [^floats pixels]
  (if (< (snapshot-size pixels)
         (custom/get-custom ::memory-threshold))
    {:type :memory
     :value pixels}
    {:type :file
     :value (Arrays/writeTemp pixels)}))

(defn read-pixels!
  "从封装结构中读取 float 数组。"
  [{:keys [type value]}]
  (case type
    :memory value
    :file   (Arrays/readTemp ^String  value)))

(defn delete-pixels!
  "若像素数组存在于临时文件，则删除。"
  [{:keys [type value]}]
  (when (= type :file)
    (try
      (.delete (File. ^String  value) )
      (catch Exception _))))


(defn- tiled-canvas-size
  "计算 tiled-canvas 中所有瓦片数据的总字节数。"
  [canvas]
  (reduce-kv (fn [sum _ tile]
               (+ sum (alength tile) 4))
             0
             (:tiles canvas)))


(defn- write-tiled-canvas-to-file
  "将 tiled-canvas 的元数据和所有瓦片写入文件。"
  [canvas file]
  (with-open [out (DataOutputStream.
                    (BufferedOutputStream.
                      (FileOutputStream. ^String file)))]
    ;; 写入元数据
    (.writeInt out (:min-tx canvas))
    (.writeInt out (:max-tx canvas))
    (.writeInt out (:min-ty canvas))
    (.writeInt out (:max-ty canvas))
    (.writeInt out (:tile-size canvas))
    ;; 写入瓦片数据（全量）
    (TiledCanvasIOUtils/writeTiles (:tiles canvas) nil out)))

(defn- read-tiled-canvas-from-file
  "从文件读取 tiled-canvas。"
  [^String file]
  (with-open [in (DataInputStream.
                   (BufferedInputStream.
                     (FileInputStream. file)))]
    (let [min-tx    (.readInt in)
          max-tx    (.readInt in)
          min-ty    (.readInt in)
          max-ty    (.readInt in)
          tile-size (.readInt in)
          tiles     (TiledCanvasIOUtils/readTiles in)]
      (tcanvas/make-canvas
        :min-tx min-tx :max-tx max-tx
        :min-ty min-ty :max-ty max-ty
        :tile-size tile-size
        :tiles tiles))))

(defn wrap-tiled-canvas
  "将 tiled-canvas 封装为快照格式。
   返回 {:type :memory/:file, :value canvas-or-path}"
  [canvas]
  (if (< (tiled-canvas-size canvas) (custom/get-custom ::memory-threshold))
    {:type :memory :value canvas}
    (let [tmp-file (File/createTempFile "krro-tile-snap-" ".dat")]
      (write-tiled-canvas-to-file canvas tmp-file)
      {:type :file :value (.getAbsolutePath tmp-file)})))

(defn read-tiled-canvas
  "从快照结构恢复 tiled-canvas。"
  [{:keys [type value]}]
  (case type
    :memory value
    :file (read-tiled-canvas-from-file (File. ^String value))))

(defn delete-tiled-canvas-snapshot!
  "清理文件型快照。"
  [{:keys [type value]}]
  (when (= type :file)
    (try (.delete (File. ^String value))
         (catch Exception _))))

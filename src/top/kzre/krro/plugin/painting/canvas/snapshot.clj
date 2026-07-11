(ns top.kzre.krro.plugin.painting.canvas.snapshot
  "快照存储策略：小快照留内存，大快照写临时文件，阈值可通过 defcustom 配置。
   提供两种快照类型的封装：
     - SnapshotData（OBB 快照，含宽高）
     - 纯 float[] 像素数组（用于图层添加/移除）"
  (:require
    [top.kzre.krro.core.custom :as custom]
    [top.kzre.krro.canvas.core.obb :as obb])
  (:import
    (top.kzre.krro.canvas.core Arrays)
    (java.io File)))

;; ═══════════════════════════════════════════════════════
;; 阈值配置
;; ═══════════════════════════════════════════════════════
(custom/defcustom ::memory-threshold
                  (* 4 1024 1024)   ; 4 MB 默认
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
      {:type :memory, :value snap}
      (let [path (obb/write-snapshot-temp! snap)]
        {:type :file, :value path}))))

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
  (if (< (snapshot-size pixels) (custom/get-custom ::memory-threshold))
    {:type :memory, :value pixels}
    {:type :file, :value (Arrays/writeTemp pixels)}))

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
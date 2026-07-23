(ns top.kzre.krro.plugin.painting.core.ops.snapshot
  "快照存储策略：小快照序列化为 Base64 字符串留在内存，大快照写临时文件。
   阈值可通过 defcustom 配置。提供纯 float[] 像素数组封装及 TiledCanvas 快照封装（支持脏瓦片过滤）。"
  (:require
    [top.kzre.krro.core.custom :as custom])
  (:import
    (java.io ByteArrayInputStream ByteArrayOutputStream File)
    (java.util Base64)
    (top.kzre.krro.plugin.painting.core.ops TiledCanvasIOUtils)
    (top.kzre.krro.util.tile TiledCanvas)))

;; ═══════════════════════════════════════════════════════
;; 阈值配置
;; ═══════════════════════════════════════════════════════
(custom/defcustom ::memory-threshold
                  (* 4 1024 1024)   ; 4 MB 默认
                  :type :integer
                  :group :krro.painting/performance
                  :doc "快照保留在内存中的最大字节数（浮点数组长度×4）。超过此值将写入临时文件。")

;; ═══════════════════════════════════════════════════════
;; TiledCanvas 快照支持（内存/文件，支持脏瓦片，内存快照可序列化）
;; ═══════════════════════════════════════════════════════
(defn- tiled-canvas-size
  "估算画布占用的内存字节数（粗略，仅用于阈值决策）。"
  [^TiledCanvas canvas]
  (let [tile-size (.getTileSize canvas)
        tile-pixels (* tile-size tile-size 4)
        bytes-per-float 4]
    (* (.tileCount canvas) tile-pixels bytes-per-float)))

(defn- serialize-tiled-canvas-to-bytes
  "将 TiledCanvas 序列化为字节数组（RLE 压缩格式）。
   dirty-tiles 为脏瓦片键集合（可为空集合，全量写入）"
  [^TiledCanvas canvas dirty-tiles]
  (let [baos (ByteArrayOutputStream.)]
    (TiledCanvasIOUtils/writeTiledCanvas canvas dirty-tiles baos)
    (.toByteArray baos)))

(defn- deserialize-tiled-canvas-from-bytes
  "从字节数组反序列化 TiledCanvas。"
  [^bytes data]
  (TiledCanvasIOUtils/readTiledCanvas (ByteArrayInputStream. data)))

(defn wrap-tiled-canvas
  "将 TiledCanvas 封装为快照格式。
   若提供 dirty-tiles (Long 键的集合)，则仅将指定脏瓦片写入快照；
   否则写入全部瓦片。
   内存快照存储为 Base64 字符串，文件快照存储为临时文件路径。
   返回 {:type :memory/:file, :value <base64-string-or-filepath>}"
  ([^TiledCanvas canvas]
   (wrap-tiled-canvas canvas nil))
  ([^TiledCanvas canvas dirty-tiles]
   (if (< (tiled-canvas-size canvas) (custom/get-custom ::memory-threshold))
     ;; 内存快照：序列化为 Base64 字符串
     (let [bytes (serialize-tiled-canvas-to-bytes canvas (set dirty-tiles))
           b64   (.encodeToString (Base64/getEncoder) bytes)]
       {:type :memory :value b64})
     ;; 文件快照：写入临时文件
     (let [tmp-file (File/createTempFile "krro-tile-snap-" ".dat")
           path     (.getAbsolutePath tmp-file)]
       (TiledCanvasIOUtils/writeTiledCanvasToFile canvas path (set dirty-tiles))
       {:type :file :value path}))))

(defn read-tiled-canvas
  "从快照结构恢复 TiledCanvas。"
  [{:keys [type value]}]
  (case type
    :memory (let [bytes (.decode (Base64/getDecoder) ^String value)]
              (deserialize-tiled-canvas-from-bytes bytes))
    :file   (TiledCanvasIOUtils/readTiledCanvasFromFile value)))

(defn delete-tiled-canvas-snapshot!
  "清理文件型快照。"
  [{:keys [type value]}]
  (when (= type :file)
    (try (.delete (File. ^String value))
         (catch Exception _))))
(ns top.kzre.krro.plugin.painting.core.project.raster-layer
  (:require
    [top.kzre.krro.canvas.core.layer.core :as lc]
    [top.kzre.krro.core.core :as kcc]
    [top.kzre.krro.core.project :as proj]
    [top.kzre.krro.core.rdb :refer [defschema]]
    [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
    [taoensso.timbre :as log])
  (:import (java.util HashMap Map Map$Entry)
           (java.util.function Consumer)
           (top.kzre.krro.util.tile TiledCanvas)))

;; 注册保护键
(proj/register-protected-key! :krro.painting/raster)

;; canvas 在侧表，适合直接封装为Java 对象
(defschema :krro.painting/raster
           :primary-key :id
           :not-null [:canvas-id :data]
           :foreign-keys [{:column :canvas-id
                           :references {:table :krro.painting/canvas :column :id}
                           :validator (fn [raster-row canvas-row]
                                        (let [layers (:layers canvas-row)]
                                          (some? (lc/find-layer (:id raster-row) layers))))
                           :on-delete :cascade}])

(def tiled-canvas-codec-plugin-def
  {:type     :krro.plugin/resource-codec
   :id       :krro.painting/tiled-canvas-codec
   :resource :krro.painting/tiled-canvas
   :pred     #(instance? TiledCanvas %)
   :encoder  (fn [^TiledCanvas c]
               (let [tile-size     (.getTileSize c)
                     default-pixel (.getDefaultPixel c)   ;; float[] 对象，由资源系统自动编码
                     tiles-atom    (atom {})]
                 (.readTiles  c
                             (reify Consumer
                               (accept [_ tile-map]
                                 (reset! tiles-atom
                                         (into {}
                                               (map (fn [^Map$Entry e]
                                                      (let [^long key (.getKey e)
                                                            pixels (.getValue e)   ;; float[]
                                                            tx (TiledCanvas/unpackTx key)
                                                            ty (TiledCanvas/unpackTy key)]
                                                        [[tx ty] pixels])))   ;; 保留 float[]，系统自动编码
                                               (.entrySet tile-map))))))
                 {:krro/type     :krro.painting/tiled-canvas
                  :tile-size     tile-size
                  :default-pixel default-pixel   ;; float[]
                  :tiles         @tiles-atom}))
   :decoder  (fn [m]
               (let [tile-size (:tile-size m)
                     ;; 资源系统已保证子节点解码，直接得到 float[]
                     default-pixel (:default-pixel m)
                     canvas (TiledCanvas. tile-size default-pixel)
                     ^Map tiles-map (HashMap.)
                     expected-len (* tile-size tile-size 4)]
                 (doseq [[[tx ty] pixels-arr] (:tiles m)]
                   (when (not= (alength pixels-arr) expected-len)
                     (throw (ex-info "Tile pixel length mismatch"
                                     {:expected expected-len
                                      :actual   (alength pixels-arr)})))
                   (.put tiles-map (TiledCanvas/pack (int tx) (int ty)) pixels-arr))
                 (.mergeTiles canvas tiles-map)
                 canvas))})


(defn create-raster!
  "创建光栅数据记录，直接使用给定的 TiledCanvas。"
  [layer-id canvas-id ^TiledCanvas canvas]
  {:pre [(keyword? layer-id)
         (keyword? canvas-id)
         (instance? TiledCanvas canvas)]}
  (kcc/insert! :krro.painting/raster {:id layer-id :canvas-id canvas-id :data canvas}))

(defn delete-raster! [layer-id]
  (kcc/delete-by-id! :krro.painting/raster layer-id))

(defn create-raster-empty!
  "创建光栅图层记录，canvas 为空瓦片画布。。"
  [layer-id canvas-id]
  (let [canvas (TiledCanvas. pc/global-tile-size)]
    (create-raster! layer-id canvas-id canvas)))

(defn create-raster*
  "无事务保证的直接 swap! 操作（用于特殊场景），canvas 为 TiledCanvas 实例。"
  [layer-id canvas-id ^TiledCanvas canvas]
  {:pre [(keyword? layer-id) (keyword? canvas-id)
         (instance? TiledCanvas canvas)]}
  (swap! proj/project assoc-in [:krro.painting/raster layer-id]
         {:id layer-id :canvas-id canvas-id :data canvas}))

;; ── 内部获取函数 ────────────────────────────────
(defn- get-raster!
  [layer-id]
  (proj/activate-resource! [:krro.painting/raster layer-id]))

;; ── 图层持久化与激活 ────────────────────────────
(defmethod pc/persistable-layer :raster [layer]
  ;; 图层结构不直接包含 canvas，只保留标识，canvas 通过侧表管理
  (dissoc layer :canvas))

(defmethod pc/persistable-layer! :raster [layer canvas-id]
  (let [canvas (:canvas layer)
        layer-id (:id layer)]
    (if (kcc/select-by-id :krro.painting/raster layer-id)
      (kcc/update-by-id! :krro.painting/raster layer-id #(assoc % :data canvas))
      (create-raster! layer-id canvas-id canvas))
    (dissoc layer :canvas)))


(defmethod pc/active-layer! :raster [layer _canvas-id]
  (if (:canvas layer)
    ;; 如果图层已经激活，返回原图层
    layer
    ;; 否则从侧表恢复
    (if-let [raster-record (get-raster! (:id layer))]
      (let [canvas (:data raster-record)]
        (assoc layer :canvas canvas))
      ;; 侧表无记录，新建空画布，并记录异常日志
      (do
        (log/warn "WARN: No raster record found for layer" (:id layer) ", creating empty canvas.")
        (assoc layer :canvas (TiledCanvas. pc/global-tile-size))))))
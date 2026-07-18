(ns top.kzre.krro.plugin.painting.core.project.raster-layer
  (:require
    [top.kzre.krro.util.tiled-canvas :as tcanvas]
    [top.kzre.krro.canvas.core.layer.core :as lc]
    [top.kzre.krro.core.core :as kcc]
    [top.kzre.krro.core.project :as proj]
    [top.kzre.krro.core.rdb :refer [defschema]]
    [top.kzre.krro.plugin.painting.core.project.canvas :as canvas]))

;; 注册保护键
(proj/register-protected-key! :krro.painting/raster)
(defschema :krro.painting/raster
           :primary-key :id
           :not-null [:canvas-id :data]
           :foreign-keys [{:column :canvas-id
                           :references {:table :krro.painting/canvas :column :id}
                           :validator (fn [raster-row canvas-row]
                                        (let [layers (:layers canvas-row)]
                                          (some? (lc/find-layer (:id raster-row) layers))))
                           :on-delete :cascade}])

;; ── 创建函数（接受 canvas map） ──────────────────
(defn create-raster!
  "创建光栅数据记录，直接使用给定的 tiled-canvas map。"
  [layer-id canvas-id canvas]
  {:pre [(keyword? layer-id)
         (keyword? canvas-id)
         (map? canvas)
         (tcanvas/valid? canvas)]}
  (kcc/insert! :krro.painting/raster {:id layer-id :canvas-id canvas-id :data canvas}))

(defn delete-raster! [layer-id]
  (kcc/delete-by-id! :krro.painting/raster layer-id))

(defn create-raster-empty!
  "创建光栅图层记录，canvas 为空瓦片画布。tile-size 可选，默认 256。"
  [layer-id canvas-id & {:keys [tile-size] :or {tile-size 256}}]
  (let [canvas (tcanvas/make-canvas :tile-size tile-size)]
    (create-raster! layer-id canvas-id canvas)))

(defn create-raster*
  "无事务保证的直接 swap! 操作（用于特殊场景），canvas 为 tiled-canvas map。"
  [layer-id canvas-id canvas]
  {:pre [(keyword? layer-id) (keyword? canvas-id)
         (map? canvas)
         (tcanvas/valid? canvas)]}
  (swap! proj/project assoc-in [:krro.painting/raster layer-id]
         {:id layer-id :canvas-id canvas-id :data canvas}))

;; ── 内部获取函数 ────────────────────────────────
(defn- get-raster!
  [layer-id]
  (proj/get-in-project! [:krro.painting/raster layer-id]))

;; ── 图层持久化与激活 ────────────────────────────
(defmethod canvas/persistable-layer :raster [layer]
  ;; 图层结构不直接包含 canvas，只保留标识，canvas 通过侧表管理
  (dissoc layer :canvas))

(defmethod canvas/persistable-layer! :raster [layer canvas-id]
  (let [canvas (:canvas layer)
        layer-id (:id layer)]
    (if (kcc/select-by-id :krro.painting/raster layer-id)
      (kcc/update-by-id! :krro.painting/raster layer-id #(assoc % :data canvas))
      (create-raster! layer-id canvas-id canvas))
    (dissoc layer :canvas)))


(defmethod canvas/active-layer! :raster [layer _canvas-id]
  (if-let [raster-record (kcc/select-by-id :krro.painting/raster (:id layer))]
    (let [canvas (:data raster-record)]
      ;; 从侧表取出画布后立即删除记录，释放侧表内存
      (kcc/delete-by-id! :krro.painting/raster (:id layer))
      (assoc layer :canvas canvas))
    ;; 侧表无记录，新建空画布
    (assoc layer :canvas (tcanvas/make-canvas :tile-size 256))))
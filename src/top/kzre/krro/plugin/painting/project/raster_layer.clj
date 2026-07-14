(ns top.kzre.krro.plugin.painting.project.raster-layer
  (:require
    [top.kzre.krro.canvas.core.canvas.raster :as raster]
    [top.kzre.krro.canvas.core.layer.core :as lc]
    [top.kzre.krro.core.core :as kcc]
    [top.kzre.krro.core.project :as proj]
    [top.kzre.krro.core.rdb :refer [defschema]]
    [top.kzre.krro.core.resources :as ress]
    [top.kzre.krro.plugin.painting.project.canvas :as canvas]
    [top.kzre.krro.core.message :as msg]))

;; 光栅数据侧表. 注册保护键，由我们自己维护.
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

(defn create-raster!
  "创建光栅数据记录，直接使用给定的 data 数组。"
  [layer-id canvas-id ^floats data]
  {:pre [(keyword? layer-id)
         (keyword? canvas-id)
         (instance? ress/float-array-class data)]}
  (kcc/insert! :krro.painting/raster {:id layer-id :canvas-id canvas-id :data data}))

(defn delete-raster! [layer-id]
  (kcc/delete-by-id! :krro.painting/raster layer-id))

(defn create-raster-empty!
  "创建光栅图层记录，data 用新建的零数组（宽高必填）。"
  [layer-id canvas-id width height]
  {:pre [(pos-int? width) (pos-int? height)]}
  (let [data (float-array (* width height 4) 0.0)]
    (create-raster! layer-id canvas-id data)))

(defn create-raster*
  "无事务保证的直接 swap! 操作（用于特殊场景）。"
  [layer-id canvas-id data]
  {:pre [(keyword? layer-id) (keyword? canvas-id)
         (instance? ress/float-array-class data)]}
  (swap! proj/project assoc-in [:krro.painting/raster layer-id]
         {:id layer-id :canvas-id canvas-id :data data}))

(defn- get-raster
  "对外表现为解码的光栅数据，引用相等.外部不该直接访问光栅侧表."
  [layer-id]
  (kcc/select-by-id :krro.painting/raster layer-id))

(defmethod canvas/persistable-layer :raster [layer]
  (dissoc layer :canvas))

(defmethod canvas/active-layer! :raster [layer width height]
  (if (:canvas layer)
    layer
    (let [pixels (:data (get-raster (:id layer)))
          ;; 如果 data 不存在，用空数组兜底
          canvas (raster/make-raster-canvas width height
                                            :data (or pixels
                                                      (do
                                                        (msg/warn "Raster layer canvas data not found! use empty pixels...")
                                                        (float-array (* width height 4) 0.0))))]

      (assoc layer :canvas canvas))))
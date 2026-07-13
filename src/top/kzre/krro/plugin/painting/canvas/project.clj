(ns top.kzre.krro.plugin.painting.canvas.project
  ""
  (:require
    [top.kzre.krro.canvas.core.canvas.raster :as raster]
    [top.kzre.krro.canvas.core.layer.core :as lc]
    [top.kzre.krro.core.core :refer [delete-by-id! insert! select-by-id update-by-id!]]
    [top.kzre.krro.core.project :as proj]
    [top.kzre.krro.core.resources :as ress]
    [top.kzre.krro.core.rdb :as rdb])
  (:import (java.util UUID)))

;; ═══════════════════════════════════════════════════════
;; 表定义与约束
;; ═══════════════════════════════════════════════════════
(defrecord CanvasData [id width height layers])

(rdb/defschema :krro.painting/canvas
               :primary-key :id
               :not-null [:id :width :height :layers]
               :defaults {:layers [] :width 800 :height 600})

(rdb/defschema :krro.painting/raster
               :primary-key :id
               :not-null [:canvas-id :data]
               :foreign-keys [{:column :canvas-id
                               :references {:table :krro.painting/canvas :column :id}
                               :validator (fn [raster-row canvas-row]
                                            (let [layers (:layers canvas-row)]
                                              (some? (lc/find-layer (:id raster-row) layers))))
                               :on-delete :cascade}])

(rdb/defschema :krro.painting/layer-meta
               :primary-key :id
               :not-null [:id :canvas-id :locked? :alpha-locked? :expanded?]
               :defaults {:locked? false :alpha-locked? false :expanded? false}
               :foreign-keys [{:column :canvas-id
                               :references {:table :krro.painting/canvas :column :id}
                               :validator (fn [meta-row canvas-row]
                                            (let [layers (:layers canvas-row)]
                                              (some? (lc/find-layer (:id meta-row) layers))))
                               :on-delete :cascade}])


;; ═══════════════════════════════════════════════════════
;; Canvas CRUD
;; ═══════════════════════════════════════════════════════

(defn create-canvas!
  "创建新画布，返回活跃的 CanvasData 实例。"
  ([id w h]
   (let [cd (CanvasData. id w h [])]   ;; 活跃对象
     (insert! :krro.painting/canvas (assoc cd :id id))
     cd))
  ([] (create-canvas! (keyword (str (UUID/randomUUID))) 800 600)))

(defn delete-canvas! [id]
  (delete-by-id! :krro.painting/canvas id))

;; ═══════════════════════════════════════════════════════
;; Raster CRUD
;; ═══════════════════════════════════════════════════════

(defn add-raster!
  ([layer-id canvas-id data]
   (insert! :krro.painting/raster {:id layer-id :canvas-id canvas-id :data data}))
 ([layer-id canvas-id width height]
  (insert! :krro.painting/raster {:id layer-id :canvas-id canvas-id :data (float-array (* width height 4))})))

;; 带星号的是没有一致性保证的方法.
(defn add-raster* [layer-id canvas-id data]
  {:pre [(keyword? layer-id)
         (keyword? canvas-id)
         (instance? ress/float-array-class data)]}
  (swap! proj/project assoc-in [:krro.painting/raster layer-id]
         {:id layer-id
          :canvas-id canvas-id
          :data data}))
(defn get-raster [layer-id]
  (select-by-id :krro.painting/raster layer-id))

(defn update-raster! [layer-id f]
  (update-by-id! :krro.painting/raster layer-id f))

(defn delete-raster! [layer-id]
  (delete-by-id! :krro.painting/raster layer-id))

;; ═══════════════════════════════════════════════════════
;; LayerMeta CRUD
;; ═══════════════════════════════════════════════════════

(defn add-layer-meta! [layer-id canvas-id]
  (insert! :krro.painting/layer-meta {:id layer-id :canvas-id canvas-id})
  layer-id)

(defn get-layer-meta [layer-id]
  (select-by-id :krro.painting/layer-meta layer-id))

(defn update-layer-meta! [layer-id f]
  (update-by-id! :krro.painting/layer-meta layer-id f))

(defn delete-layer-meta! [layer-id]
  (delete-by-id! :krro.painting/layer-meta layer-id))


(defn canvas-data ^CanvasData
  ([id]
   (select-by-id :krro.painting/canvas id))
  ([id db-map]
   (get-in db-map [:krro.painting/canvas id] )))

(defn canvas-data!
  (^CanvasData [id]
   (proj/get-in-project! [:krro.painting/canvas id])))

(defn visible-layer?
  ([canvas-id layer-id]
   (when-let [cd (canvas-data canvas-id)]
     (when-let [l (lc/find-layer layer-id (:layers cd))]
       (:visible? l))))
  ([canvas-id layer-id db-map]
   (when-let [cd (canvas-data canvas-id db-map)]
     (when-let [l (lc/find-layer layer-id (:layers cd))]
       (:visible? l)))))


(defn layers-by-id!
  ([canvas-id] (:layers (canvas-data! canvas-id))))

(defn find-layer-in-canvas! [canvas-id layer-id]
  (when-let [ls (layers-by-id! canvas-id)]
    (lc/find-layer layer-id ls)))

(defn canvas-size [canvas-id] (when-let [cd (canvas-data! canvas-id)] [(:width cd) (:height cd)]))


(defmulti persistable-layer :type)
(defmulti active-layer! (fn [layer _width _height] (:type layer)))

(defmethod persistable-layer :default [layer] layer)

(defmethod persistable-layer :raster [layer] (dissoc layer :canvas))

(defmethod active-layer! :default [layer _w _h] layer)

(defmethod active-layer! :raster [layer width height]
  (if (:canvas layer)
    layer
    (let [pixels (:data (get-raster (:id layer)))
          canvas (raster/make-raster-canvas width height :data pixels)]
      (assoc layer :canvas canvas))))

;; ═══════════════════════════════════════════════════════
;; 编解码器（直接使用多方法）
;; ═══════════════════════════════════════════════════════

(def canvas-codec-plugin-def
  {:type     :krro.plugin/resource-codec
   :id       :krro.painting/canvas-codec
   :resource :krro.painting/canvas-data
   :pred     #(instance? CanvasData % )
   :encoder  (fn [c]
               (let [encoded-layers (mapv persistable-layer (:layers c))]
                 {:krro/type :krro.painting/canvas-data
                  :id (:id c)
                  :width  (:width c)
                  :height (:height c)
                  :layers encoded-layers}))
   :decoder  (fn [m]
               (let [id (:id m)
                     w (:width m)
                     h (:height m)
                     decoded-layers (mapv #(active-layer! % w h) (:layers m))]
                 (map->CanvasData {:id id :width w :height h :layers decoded-layers})))})


;; ═══════════════════════════════════════════════════════
;; 激活 / 反激活（委托给 resource 系统）
;; ═══════════════════════════════════════════════════════

(defn activate-canvas!
  "将画布数据从代理 map 激活为 CanvasData 实例。"
  [canvas-id]
  (proj/get-in-project! [:krro.painting/canvas canvas-id]))

(defn deactivate-canvas!
  "将 CanvasData 编码回代理 map。"
  [canvas-id]
  (proj/deactivate-resource! [:krro.painting/canvas canvas-id]))

;; ═══════════════════════════════════════════════════════
;; polyfill
;; ═══════════════════════════════════════════════════════

(defn ensure-canvas-data!
  "确保画布存在并返回激活的 CanvasData。"
  [canvas-id width height]
  (if-let [cd (activate-canvas! canvas-id)]
    cd
    (let [_ (create-canvas! canvas-id width height)]
      (activate-canvas! canvas-id))))
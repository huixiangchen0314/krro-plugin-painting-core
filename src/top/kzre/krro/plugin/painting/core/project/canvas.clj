(ns top.kzre.krro.plugin.painting.core.project.canvas
  "画布数据，负责定义总体画布结构，并提供多方法供图层拓展."
  (:require
    [clojure.spec.alpha :as s]
    [top.kzre.krro.canvas.core.layer.core :as lc]
    [top.kzre.krro.core.core :as kcc]
    [top.kzre.krro.core.project :as proj]
    [top.kzre.krro.core.rdb :refer [defschema]]
    [top.kzre.krro.plugin.painting.core.edit.falloff :as-alias falloff]
    [top.kzre.krro.plugin.painting.core.edit.snap :as-alias snap]
    [top.kzre.krro.plugin.painting.core.edit.spec :as-alias edit])
  (:import
    (java.util UUID)))

;; 全局瓦片大小 256x256
(defonce global-tile-size 256 )

;; 水平镜像编辑
(s/def ::horizontal-mirror-enabled boolean?)
;; 垂直镜像编辑
(s/def ::vertical-mirror-enabled boolean?)
(s/def ::horizontal-mirror-center number?)
(s/def ::vertical-mirror-center number?)

(s/def ::tiling-enabled boolean?)
;;变换轴心点
(s/def ::pivot-center ::edit/pivot-center)
(s/def ::snap-options ::snap/snap-options)
(s/def ::falloff-options ::falloff/falloff-options)
(s/def ::id keyword?)
(s/def ::current-layer-id keyword?)
(s/def ::width number?)
(s/def ::height number?)
(s/def ::layers vector?)

(s/def ::canvas-data
  (s/keys
    :req-un [::id ::width ::height ::layers]
    :opt-un [::current-layer-id
             ::pivot-center
             ::snap-options
             ::falloff-options
             ::horizontal-mirror-enabled
             ::horizontal-mirror-center
             ::vertical-mirror-enabled
             ::vertical-mirror-center
             ::tiling-enabled]))

;; 定义记录以便自定义编解码.
(defrecord CanvasData [id width height layers current-layer-id])

(defschema :krro.painting/canvas
               :primary-key :id
               :spec ::canvas-data                          ;; 利用spec 进行 rdb 约束
               :not-null [:id :width :height :layers]
               :defaults {:layers []})

(defn create-canvas!
  "创建空白画布"
  ([w h] (create-canvas! (keyword (str (UUID/randomUUID))) w h))
  ([id w h]
   (let [cd (CanvasData. id w h [] nil)]   ;; 将测试图层放入 layers 向量
     (kcc/insert! :krro.painting/canvas (assoc cd :id id))
     cd)))


(defn delete-canvas!
  "删除画布，相关资源由 rdb 负责级联删除."
  [id]
  (kcc/delete-by-id! :krro.painting/canvas id))

(defn save-canvas-data!
  "创建或更新画布数据"
  [canvas-id cd]
  (kcc/update-by-id! :krro.painting/canvas canvas-id (constantly cd)))

(defn canvas-data
  "查询画布数据，当查询的是非代理数据或确认数据已经激活使用允许使用."
  ^CanvasData
  ([canvas-id]
   (kcc/select-by-id :krro.painting/canvas canvas-id))
  ([canvas-id db-map]                                              ;; 从指定map查询数据，用于双向绑定检查更新
   (get-in db-map [:krro.painting/canvas canvas-id])))

(defn canvas-data!
  "查询并激活画布数据."
  ^CanvasData [canvas-id]
  (proj/get-in-project! [:krro.painting/canvas canvas-id]))

(defn ensure-canvas-data!
  "确保画布存在并返回激活的 CanvasData。"
  [canvas-id width height]
  (or (canvas-data! canvas-id)
      (create-canvas! canvas-id width height)))

(defn canvas-size
  "查询画布尺寸，这两个是非代理资源，保持键一致就能多态获取了,无需激活."
  [canvas-id]
  (when-let [cd (canvas-data! canvas-id)]
    [(:width cd) (:height cd)]))

(defn deactivate-canvas!
  "将 CanvasData 编码回代理 map, 日后拓展虚拟代理时候可以把画布数据保存到外部."
  [canvas-id]
  (proj/deactivate-resource! [:krro.painting/canvas canvas-id]))

(defn current-layer-id
  ([canvas-id]
   (when-let [cd (canvas-data canvas-id)]
     (:current-layer-id cd)))
  ([canvas-id db-map]
   (when-let [cd (canvas-data canvas-id db-map)]
     (:current-layer-id cd))))

;; 持久化多方法.
(defmulti persistable-layer :type)
(defmulti persistable-layer! (fn [layer _canvas-id] (:type layer)))
(defmethod persistable-layer! :default
  [layer _canvas-id] (persistable-layer layer))
(defmulti active-layer! (fn [layer _canvas-id] (:type layer)))

(defmethod persistable-layer :default [layer] layer)

(defmethod active-layer! :default [layer _canvas-id] layer)


(def canvas-codec-plugin-def
  {:type     :krro.plugin/resource-codec
   :id       :krro.painting/canvas-codec
   :resource :krro.painting/canvas-data
   :pred     #(instance? CanvasData % )
   :encoder  (fn [c _ctx]
               (let [id (:id c)
                     encoded-layers (mapv #(persistable-layer! % id) (:layers c))]
                 {:krro/type :krro.painting/canvas-data
                  :id id
                  :width  (:width c)
                  :height (:height c)
                  :layers encoded-layers
                  :current-layer-id (:current-layer-id c)}))
   ;; 解码时候自己负责恢复句柄
   :decoder  (fn [m]
               (let [id (:id m)
                     w (:width m)
                     h (:height m)
                     decoded-layers (mapv #(active-layer! % id) (:layers m))]
                 (map->CanvasData {:id id :width w :height h :layers decoded-layers
                                   :current-layer-id (:current-layer-id m)})))})



;; 其他低级查询
(defn layers-by-id
  ([canvas-id] (:layers (canvas-data canvas-id))))

(defn layers-by-id!
  ([canvas-id] (:layers (canvas-data! canvas-id))))

(defn find-layer-in-canvas! [canvas-id layer-id]
  (when-let [ls (layers-by-id! canvas-id)]
    (lc/find-layer layer-id ls)))

(defn visible-layer?
  ([canvas-id layer-id]
   (when-let [cd (canvas-data canvas-id)]
     (when-let [l (lc/find-layer layer-id (:layers cd))]
       (:visible? l))))
  ([canvas-id layer-id db-map]
   (when-let [cd (canvas-data canvas-id db-map)]
     (when-let [l (lc/find-layer layer-id (:layers cd))]
       (:visible? l)))))

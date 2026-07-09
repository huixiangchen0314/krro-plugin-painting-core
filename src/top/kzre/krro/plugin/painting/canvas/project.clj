(ns top.kzre.krro.plugin.painting.canvas.project
  "项目数据集成：画布与图层元数据管理。基于 rdb 提供结构化的数据访问。
   光栅图层像素数据存储于顶层受保护侧表，仅首次创建时注册，后续直接引用。"
  (:require [top.kzre.krro.core.core :refer [insert! select-by-id update-by-id! delete-by-id! path-select]]
            [top.kzre.krro.core.project :as proj]
            [top.kzre.krro.core.rdb :as rdb]
            [top.kzre.krro.canvas.raster.core :as rl]
            [top.kzre.krro.canvas.core.canvas.protocol :as cp]
            [top.kzre.krro.core.resource :as res]))

;; ═══════════════════════════════════════════════════════
;; Schema 定义
;; ═══════════════════════════════════════════════════════
(rdb/defschema :krro.painting/raster
               :primary-key :id
               :not-null [:data]
               :unique [:id])

(rdb/defschema :krro.painting/canvas
               :primary-key :id)

(rdb/defschema :krro.painting/layer-meta
               :primary-key :id)

;; ═══════════════════════════════════════════════════════
;; 像素数据侧表操作
;; ═══════════════════════════════════════════════════════
(defn register-raster!
  "将像素数组存入 raster 表，返回唯一 ID。仅首次创建时调用。"
  [pixels]
  (let [id (keyword (str (gensym "raster-")))]
    (insert! :krro.painting/raster {:id id :data pixels})
    id))

(defn get-raster
  "根据 ID 获取像素数组，确保通过 get-in-project! 激活。"
  [id]
  (when-let [path (path-select :krro.painting/raster id)]
    (:data (proj/get-in-project! path))))

(defn remove-raster!
  "从 raster 表移除像素数据。"
  [id]
  (delete-by-id! :krro.painting/raster id))

;; ═══════════════════════════════════════════════════════
;; 画布数据容器
;; ═══════════════════════════════════════════════════════
(defrecord CanvasData [width height layers])

;; ═══════════════════════════════════════════════════════
;; 资源编解码器（复用已有引用）
;; ═══════════════════════════════════════════════════════
(def canvas-codec-plugin-def
  {:type     :krro.plugin/resource-codec
   :id       :krro.painting/canvas-codec
   :resource :krro.painting/canvas-data
   :encoder (fn [^CanvasData c]
              (let [encoded-layers
                    (mapv (fn [layer]
                            (if (= :raster (:type layer))
                              ;; 光栅图层：移除 :canvas，若已有引用则复用，否则首次注册
                              (let [layer' (dissoc layer :canvas)]
                                (if (:krro.painting/raster-ref layer')
                                  layer'
                                  (let [pixels (cp/data (:canvas layer))
                                        raster-id (register-raster! pixels)]
                                    (assoc layer' :krro.painting/raster-ref raster-id))))
                              ;; 非光栅图层：正常编码
                              (res/encode layer)))
                          (:layers c))]
                {:krro/type :krro.painting/canvas-data
                 :width  (:width c)
                 :height (:height c)
                 :layers encoded-layers}))
   :decoder  (fn [m]
               (let [decoded-layers
                     (mapv (fn [layer]
                             (if (:krro.painting/raster-ref layer)
                               (let [pixels (get-raster (:krro.painting/raster-ref layer))
                                     canvas (rl/make-raster-layer (:width m) (:height m) :data pixels)
                                     c (:canvas canvas)]
                                 (-> layer
                                     (dissoc :krro.painting/raster-ref)
                                     (assoc :canvas c)))
                               (res/realize layer)))
                           (:layers m))]
                 (map->CanvasData {:width (:width m) :height (:height m) :layers decoded-layers})))})

;; ═══════════════════════════════════════════════════════
;; polyfill 函数
;; ═══════════════════════════════════════════════════════
(defn polyfill-canvas-data!
  "确保画布存在，首次创建时保留运行时必需的 :canvas 字段，同时建立侧表引用。"
  [canvas-id width height]
  (if-let [canvas-map (select-by-id :krro.painting/canvas canvas-id)]
    (map->CanvasData canvas-map)
    (let [default-layer (rl/make-raster-layer width height)
          canvas        (:canvas default-layer)
          pixels        (cp/data canvas)
          raster-id     (register-raster! pixels)
          ;; 保留 :canvas 以保证外部运行时操作正常，同时标记侧表引用
          layer-ref     (assoc default-layer :krro.painting/raster-ref raster-id)
          new-canvas    (map->CanvasData {:width width :height height :layers [layer-ref]})]
      (insert! :krro.painting/canvas (assoc new-canvas :id canvas-id))
      new-canvas)))

;; ═══════════════════════════════════════════════════════
;; LayerMeta 记录
;; ═══════════════════════════════════════════════════════
(defrecord LayerMeta [locked? alpha-locked? expanded?])

(def layer-meta-codec-plugin-def
  {:type     :krro.plugin/resource-codec
   :id       :krro.painting/layer-meta-codec
   :resource :krro.painting/layer-meta
   :encoder  (fn [^LayerMeta m]
               {:krro/type      :krro.painting/layer-meta
                :locked?        (:locked? m)
                :alpha-locked?  (:alpha-locked? m)
                :expanded?      (:expanded? m)})
   :decoder  (fn [m]
               (map->LayerMeta {:locked?       (boolean (:locked? m))
                                :alpha-locked? (boolean (:alpha-locked? m))
                                :expanded?     (boolean (:expanded? m))}))})

(defn polyfill-layer-meta!
  [canvas-id layer-id]
  (if-let [meta-map (select-by-id :krro.painting/layer-meta layer-id)]
    (map->LayerMeta meta-map)
    (let [new-meta (map->LayerMeta {:locked? false :alpha-locked? false :expanded? false})]
      (insert! :krro.painting/layer-meta (assoc new-meta :id layer-id))
      new-meta)))
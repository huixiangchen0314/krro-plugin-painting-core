(ns top.kzre.krro.plugin.painting.project.layer-meta
  (:require
    [top.kzre.krro.canvas.core.layer.core :as lc]
    [top.kzre.krro.core.core :as kcc]
    [top.kzre.krro.core.rdb :refer [defschema]]))

;; 图层编辑器数据，使用单表继承.
(defschema :krro.painting/layer-meta
                ;; layer-meta 的id 即 所引用的图层 id
               :primary-key :id
               :not-null [:id :canvas-id
                          :name
                          :locked? :alpha-locked?
                          ;; 拓展数据，不要求非空
                          ;; :expanded?
                          ]
               :defaults {:locked? false :alpha-locked? false
                          }
               :foreign-keys [{:column :canvas-id
                               :references {:table :krro.painting/canvas
                                            :column :id}
                               ;; layer-meta 所引用的画布必须存在指定图层.
                               :validator (fn [meta-row canvas-row]
                                            (let [layers (:layers canvas-row)]
                                              (some? (lc/find-layer (:id meta-row) layers))))
                               :on-delete :cascade}])

(defn create-layer-meta!
  "创建图层元信息，注意删除由rdb级联删除，我们不做业务删除."
  [layer-id canvas-id
   & {:keys [name]
      :or {name "Unnamed Layer"}}]
  (kcc/insert! :krro.painting/layer-meta {:id layer-id :canvas-id canvas-id :name name}))

(defn layer-meta
  ([layer-id]
   (kcc/select-by-id :krro.painting/layer-meta layer-id))
  ([layer-id db-map]                                              ;; 从指定map查询数据，用于双向绑定检查更新
   (get-in db-map [:krro.painting/layer-meta layer-id])))

(defn set-layer-locked! [layer-id locked?]
  (kcc/update-by-id! :krro.painting/layer-meta layer-id
                     #(assoc % :locked? locked?)))

(defn set-layer-alpha-locked! [layer-id alpha-locked?]
  (kcc/update-by-id! :krro.painting/layer-meta layer-id
                     #(assoc % :alpha-locked? alpha-locked?)))

(defn set-layer-name! [layer-id layer-name]
  (kcc/update-by-id! :krro.painting/layer-meta layer-id
                     #(assoc % :name layer-name)))

(defn set-layer-group-expanded! [layer-id expanded?]
  (kcc/update-by-id! :krro.painting/layer-meta layer-id
                     #(assoc % :expanded? expanded?)))
(defn layer-locked? [layer-id]
  (when-let [m (layer-meta layer-id)]
    (:locked? m)))

(defn layer-alpha-locked? [layer-id]
  (when-let [m (layer-meta layer-id)]
    (:alpha-locked? m)))

(defn layer-name [layer-id]
  (when-let [m (layer-meta layer-id)]
    (:name m)))

(defn layer-group-expanded? [layer-id]
  (when-let [m (layer-meta layer-id)]
    (:expanded? m)))
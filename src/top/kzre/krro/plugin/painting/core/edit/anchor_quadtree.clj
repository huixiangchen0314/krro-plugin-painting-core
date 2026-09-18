(ns top.kzre.krro.plugin.painting.core.edit.anchor-quadtree
  (:require
    [top.kzre.krro.canvas.core.layer.util :as util]
    [top.kzre.krro.core.reframe.core :as rf]
    [top.kzre.krro.plugin.painting.core.algo.anchor-quadtree :as anchor-quadtree]
    [top.kzre.krro.plugin.painting.core.store :as store]))

;; 锚点四叉树加速结构
;; 存储形态：{canvas-id {:layer-id <layer-id> :tree <quadtree>}}
(defonce anchor-quadtrees (atom {}))

(defmacro quadtree-fn
  "获取 canvas-id 对应的四叉树，若存在则调用 fn-sym，并将 tree 作为第一个参数插入。"
  [canvas-id fn-sym & args]
  `(when-let [entry# (get @anchor-quadtrees ~canvas-id)]
     (when-let [~'tree (:tree entry#)]
       (~fn-sym ~'tree ~@args))))

(defn- build-for-layer
  [layer]
  (when (= :vector (:type layer))
    (anchor-quadtree/build-anchor-quadtree (:paths layer))))

(defn anchor-quadtree-interceptor
  "确保 anchor-quadtree 存在且与当前图层一致。
   不一致（图层切换 / canvas 切换）则清空重建。"
  []
  {:before
   (fn [context]
     (when-let [canvas-id (get-in context [:coeffects :record-id])]
       (let [canvas-data (get-in context [:coeffects :record :canvas-data])]
         (when-let [current-layer-id (:current-layer-id canvas-data)]
           (let [layers (:layers canvas-data)
                 entry  (get @anchor-quadtrees canvas-id)]
             ;; 图层不一致——重建
             (if (not= (:layer-id entry) current-layer-id)
               (when-let [layer (util/find-layer current-layer-id layers)]
                 (when-let [tree (build-for-layer layer)]
                   (swap! anchor-quadtrees assoc canvas-id
                          {:layer-id current-layer-id
                           :tree     tree})))
               ;; 图层一致——检查 tree 是否存在（比如首次构建时 layer 不是 vector）
               (when (nil? (:tree entry))
                 (when-let [layer (util/find-layer current-layer-id layers)]
                   (when-let [tree (build-for-layer layer)]
                     (swap! anchor-quadtrees assoc canvas-id
                            {:layer-id current-layer-id
                             :tree     tree})))))))))
     context)})

(defn update-anchors!
  [canvas-id old-paths new-paths anchors]
  (quadtree-fn canvas-id anchor-quadtree/update-anchors! old-paths new-paths anchors))

(defn delete-anchors!
  [canvas-id paths anchors]
  (quadtree-fn canvas-id anchor-quadtree/delete-anchors! paths anchors))

(defn insert-anchors!
  [canvas-id paths anchors]
  (quadtree-fn canvas-id anchor-quadtree/insert-anchors! paths anchors))

(defn delete-path!
  [canvas-id paths path-id]
  (quadtree-fn canvas-id anchor-quadtree/delete-path! paths path-id))

(defn insert-path!
  [canvas-id paths path-id]
  (quadtree-fn canvas-id anchor-quadtree/insert-path! paths path-id))

(rf/reg-fx
  store/app-id :anchor-quadtree/update-anchors
  (fn [_ record-id old-paths new-paths anchors]
    (update-anchors! record-id old-paths new-paths anchors)))

(rf/reg-fx
  store/app-id :anchor-quadtree/delete-anchors
  (fn [_ record-id paths anchors]
    (delete-anchors! record-id paths anchors)))

(rf/reg-fx
  store/app-id :anchor-quadtree/insert-anchors
  (fn [_ record-id paths anchors]
    (insert-anchors! record-id paths anchors)))

(rf/reg-fx
  store/app-id :anchor-quadtree/insert-path
  (fn [_ record-id paths path-id]
    (insert-path! record-id paths path-id)))

(rf/reg-fx
  store/app-id :anchor-quadtree/delete-path
  (fn [_ record-id paths path-id]
    (delete-path! record-id paths path-id)))

(rf/reg-fx
  store/app-id :anchor-quadtree/rebuild
  (fn [_ record-id layer-id paths]
    (let [tree (anchor-quadtree/build-anchor-quadtree paths)]
      (swap! anchor-quadtrees assoc record-id
             {:layer-id layer-id
              :tree     tree}))))

(rf/reg-fx
  store/app-id :anchor-quadtree/close
  (fn [_ record-id]
    (swap! anchor-quadtrees dissoc record-id)))
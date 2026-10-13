(ns top.kzre.krro.plugin.painting.core.schedule.layer
  (:import (top.kzre.krro.util.tile TiledCanvas)))

(defn- diff-layer! [old-layer new-layer]
  ;; 光栅图层
  (when (= :raster
           (:type old-layer)
           (:type new-layer))
    (let [^TiledCanvas old-canvas (:canvas old-layer)
          ^TiledCanvas new-canvas (:canvas new-layer)]
      (.compareAndMerge new-canvas old-canvas)
      )))

(defn- build-map
  "摊平成 layer-id->layer 的map"
  [layers]
  (persistent!
    (reduce (fn [acc l]
              (assoc! acc (:id l) l))
            (transient {})
            layers)))


(defn- diff-map
  "遍历旧map，如果新map也有一样id的，就diff-layer!"
  [old-map new-map]
  (reduce-kv
    (fn [_ id old-layer]
      (when-let [new-layer (get new-map id)]
        (diff-layer! old-layer new-layer)))
    nil
    old-map))


(defn diff!
  "图层节点合并diff。

   把 old-layers 中比 new-layers 更新的瓦片，合并进 new-layers 对应
   图层的 canvas——原地修改。返回 new-layers，方便链式调用。

   - old-layers / new-layers 均为序列，元素携带 :id 字段标识图层
   - 只在 old 和 new 都存在同一 :id 时做 diff
   - 只处理 :raster 类型图层；其他类型跳过"
  [old-layers new-layers]
  (diff-map (build-map old-layers) (build-map new-layers))
  new-layers)
(ns top.kzre.krro.plugin.painting.core.oplog.vector-layer
  "矢量路径 oplog 事件。

   三个事件：
     - :oplog/vector-path-added          —— 路径添加（预览与提交共用）
     - :oplog/vector-path-removed        —— 路径移除（预览清理）
     - :oplog/vector-layer-paths-dirty   —— 全量替换（回滚）

   事务只传变更前后的 paths——事件处理器构造 change 并派发到 :oplog/log。

   和 raster 的 :oplog/brush-stroke 相比：
     - raster 在这里渲染 stroke（stroke → canvas）
     - vector 已在事务里渲染（stroke → path）——这里只做打包"
  (:require
    [top.kzre.krro.core.reframe.core :as rf]
    [top.kzre.krro.plugin.painting.core.changes.vector-layer :as change]
    [top.kzre.krro.plugin.painting.core.project.vector-layer :as pv]
    [top.kzre.krro.plugin.painting.core.record :as record]
    [top.kzre.krro.plugin.painting.core.store :as store]))

(rf/reg-event-fx
  store/app-id :oplog/vector-path-added
  (fn [cofx [_ canvas-id layer-id path-id path & {:as ctx}]]
    (let [{:keys [layer-transform]} (record/layer-context (:record cofx) layer-id)
          op (change/make-vector-path-added layer-id path-id path layer-transform)]
      {:dispatch [:oplog/log canvas-id op ctx]})))

(rf/reg-event-fx
  store/app-id :oplog/vector-path-removed
  (fn [cofx [_ canvas-id layer-id path-id & {:as ctx}]]
    (let [{:keys [layer layer-transform]} (record/layer-context (:record cofx) layer-id)
          dirty-paths (pv/paths layer)
          op (change/make-vector-path-removed
               layer-id path-id (get dirty-paths path-id) layer-transform)]
      {:dispatch [:oplog/log canvas-id op ctx]})))

(rf/reg-event-fx
  store/app-id :oplog/vector-path-updated
  (fn [cofx [_ canvas-id layer-id path-id new-path & {:as ctx}]]
    (let [{:keys [layer layer-transform]} (record/layer-context (:record cofx) layer-id)
          dirty-paths (pv/paths layer)
          op (change/make-vector-path-updated
               layer-id path-id
               (get dirty-paths path-id)
               new-path layer-transform)]
      {:dispatch [:oplog/log canvas-id op ctx]})))

(rf/reg-event-fx
  store/app-id :oplog/vector-layer-paths-dirty
  (fn [cofx [_ canvas-id layer-id old-paths new-paths & {:as ctx}]]
    (let [{:keys [layer-transform]} (record/layer-context (:record cofx) layer-id)
          dirty-tiles (into {} (mapcat pv/path-tiles new-paths))
          op (change/make-vector-layer-paths-dirty
               layer-id old-paths new-paths dirty-tiles layer-transform)]
      {:dispatch [:oplog/log canvas-id op ctx]})))
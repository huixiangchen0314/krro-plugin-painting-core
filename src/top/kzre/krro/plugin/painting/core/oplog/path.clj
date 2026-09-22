(ns top.kzre.krro.plugin.painting.core.oplog.path
  "矢量路径 oplog 事件（基础）。

   三个事件：
     - :oplog/vector-path-saved          —— 路径保存（save 语义——不区分新增/更新）
     - :oplog/vector-path-deleted        —— 路径删除
     - :oplog/vector-layer-paths-dirty   —— 全量替换（回滚 / 导入）

   事件接收意图——内部构造 paths map——调 change make- 派发 :oplog/log。

   所有几何变化统一走 make-vector-paths-geometry-changed——
   save 语义 + :saved / :deleted 集合。"
  (:require
    [top.kzre.krro.core.reframe.core :as rf]
    [top.kzre.krro.plugin.painting.core.changes.path :as change]
    [top.kzre.krro.plugin.painting.core.project.vector-layer :as pv]
    [top.kzre.krro.plugin.painting.core.record :as record]
    [top.kzre.krro.plugin.painting.core.store :as store]))

(rf/reg-event-fx
  store/app-id :oplog/vector-path-saved
  (fn [cofx [_ canvas-id layer-id path-id path & {:as ctx}]]
    (let [{:keys [layer layer-transform]} (record/layer-context (:record cofx) layer-id)
          old-paths (pv/paths layer)
          new-paths (assoc old-paths path-id path)
          op        (change/make-vector-paths-geometry-changed
                      layer-id old-paths new-paths layer-transform
                      :saved #{path-id})]
      {:dispatch [:oplog/log canvas-id op ctx]})))

(rf/reg-event-fx
  store/app-id :oplog/vector-path-deleted
  (fn [cofx [_ canvas-id layer-id path-id & {:as ctx}]]
    (let [{:keys [layer layer-transform]} (record/layer-context (:record cofx) layer-id)
          old-paths (pv/paths layer)
          new-paths (dissoc old-paths path-id)
          op        (change/make-vector-paths-geometry-changed
                      layer-id old-paths new-paths layer-transform
                      :deleted #{path-id})]
      {:dispatch [:oplog/log canvas-id op ctx]})))

(rf/reg-event-fx
  store/app-id :oplog/vector-layer-paths-dirty
  (fn [cofx [_ canvas-id layer-id old-paths new-paths & {:as ctx}]]
    (let [{:keys [layer-transform]} (record/layer-context (:record cofx) layer-id)
          op        (change/make-vector-paths-geometry-changed
                      layer-id old-paths layer-transform
                      :saved   new-paths
                      :deleted (into #{} (remove #(contains? new-paths %))
                                     (keys old-paths))
                      :full?   true)]
      {:dispatch [:oplog/log canvas-id op ctx]})))
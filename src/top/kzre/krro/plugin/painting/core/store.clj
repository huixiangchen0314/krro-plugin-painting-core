(ns top.kzre.krro.plugin.painting.core.store
  "项目数据 db 定义，基于 reframe record 隔离"
  (:require
    [taoensso.timbre :as log]
    [top.kzre.krro.core.core :as kcc]
    [top.kzre.krro.core.reframe :as rf]
    [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
    [top.kzre.krro.plugin.painting.core.state :as state]))


;; 固定 app-id，所有画布共享同一个应用实例（事件/订阅定义隔离于该 app-id）
(defonce app-id :krro.painting)

;; 记录每个画布 store 的注销函数，key 为 canvas-id
(defonce ^:private store-registry (atom {}))

(defonce ^:private record-data (atom {}))

(defn record [canvas-id]
  (merge
    (get @record-data canvas-id)
    {:canvas-id   canvas-id
     :canvas-data (pc/canvas-data! canvas-id)
     :canvas-state (state/canvas-runtime canvas-id)}))

(defn- update-record! [{:keys [canvas-id canvas-data canvas-state]
                       :as record}]
  (when canvas-data (kcc/update-by-id! :krro.painting/canvas canvas-id (constantly canvas-data)))
  (when canvas-state (swap! state/canvas-runtimes assoc canvas-id canvas-state))
  (swap! record-data assoc canvas-id (dissoc record :canvas-data :canvas-state)))

(defn reg-canvas-store
  "为指定 canvas-id 注册 reframe store（作为一个独立 record）。
   若已注册则直接返回已有的注销函数，防止重复注册。"
  [canvas-id]
  (if-let [stop-fn (get @store-registry canvas-id)]
    (do
      (log/warn "canvas store already registered for canvas-id:" canvas-id)
      stop-fn)
    (let [getter (fn [rid]
                   ;; rid 即 canvas-id，构造当前 record 数据
                   (record rid))
          setter (fn [rid new-record]
                   ;; 保证 record-id 一致
                   (update-record! (assoc new-record :canvas-id rid)))
          stop-fn (rf/reg-store app-id canvas-id getter setter)]
      (swap! store-registry assoc canvas-id stop-fn)
      stop-fn)))

(defn unreg-canvas-store
  "注销指定 canvas-id 的 reframe store，停止事件循环"
  [canvas-id]
  (when-let [stop-fn (get @store-registry canvas-id)]
    (stop-fn)
    (swap! store-registry dissoc canvas-id)
    (swap! record-data dissoc canvas-id)
    ;; TODO 添加hook，方便最终清理检查
    (log/info "unregistered store for canvas-id:" canvas-id)))
(ns top.kzre.krro.plugin.painting.core.session.store
  (:require [taoensso.timbre :as log]
            [top.kzre.krro.core.reframe.core :as rf]
            [top.kzre.krro.plugin.painting.core.brush.core :as brush]
            [top.kzre.krro.ui.core.bind :as bind])
  (:import (java.time Instant)))

(defonce ^:private app-id* ::session)

(defn app-id [] app-id*)

(defonce ^:private store-registry (atom {}))
(defonce ^:private session-data* (atom {}))

(defonce ^:private local-session*
         (keyword (str *ns*)
                  (str "krro-painting-local-session-" (.toEpochMilli (Instant/now)))))
(defonce ^:private session-bind-ctx* (bind/create session-data*))
(defn local-session-id [] local-session*)

(defn session
  ([] (session (local-session-id)))
  ([session-id]
   (merge
     {:brush brush/default-brush}
     (get @session-data* session-id)
          {:session-id session-id})))

(defn session-bind-ctx [] session-bind-ctx*)

(defn- update-record! [{:keys [session-id]
                        :as session}]
  (swap! session-data* assoc session-id session))

(defn reg-session
  [session-id]
  (if-let [stop-fn (get @store-registry session-id)]
    (do
      (log/warn "session store already registered for session-id:" session-id)
      stop-fn)
    (let [getter (fn [rid]
                   ;; rid 即 session-id，构造当前 record 数据
                   (session rid))
          setter (fn [rid new-record]
                   ;; 保证 record-id 一致
                   (update-record! (assoc new-record :canvas-id rid)))
          stop-fn (rf/reg-store app-id session-id getter setter)]
      (swap! store-registry assoc session-id stop-fn)
      stop-fn)))

(defn unreg-session
  [canvas-id]
  (when-let [stop-fn (get @store-registry canvas-id)]
    (stop-fn)
    (swap! store-registry dissoc canvas-id)
    (swap! session-data* dissoc canvas-id)
    ;; TODO 添加hook，方便最终清理检查
    (log/warn "unregistered store for session-id:" canvas-id)))

;; 注册本地session-id
(reg-session (local-session-id))
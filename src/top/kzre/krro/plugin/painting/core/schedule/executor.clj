(ns top.kzre.krro.plugin.painting.core.schedule.executor
  "执行环境准备"
  (:require
    [taoensso.timbre :as log]
    [top.kzre.krro.core.util.promise :as promise]
    [top.kzre.krro.plugin.painting.core.layer.clone :as clone]
    [top.kzre.krro.plugin.painting.core.layer.dispose :as dispose]
    [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
    [top.kzre.krro.plugin.painting.core.schedule.protocol :as proto])
  (:import
    (java.util.concurrent Executors ExecutorService ThreadFactory TimeUnit)
    (top.kzre.krro.core.util
      CoalescedTask
      ExecutorServiceAsyncAdapter
      LatestTaskRunner
      Mergable)))

(defn- as-vec
  "把单组脏参数规范化为向量。
   向量 → 保持；其他 → 包一层。"
  [x]
  (if (vector? x) x [x]))

(defrecord Params
  [task-id
   scheduler
   canvas
   layers
   tile-size
   dirty-tiles
   dirty-transform
   image-width
   image-height
   current-layer-id
   viewport
   viewport-w
   viewport-h]
  Mergable
  (merge [this other]
    (log/debug "merge scheduler task.")
    ;; ── 释放 this 的克隆图层——每个单独捕获
    (when-let [old-cloned (:layers this)]
      (doseq [layer old-cloned]
        (try
          (dispose/dispose-layer layer)
          (catch Throwable t
            (log/error t "dispose cloned layer failed"
                       {:layer-id (:id layer)})))))

    ;; ── viewport 变换 → 脏瓦片无法合并 → 全量
    (let [viewport-changed? (not= (:viewport this) (:viewport other))]
      (if viewport-changed?
        (-> other
            (assoc :dirty-tiles     nil)
            (assoc :dirty-transform nil))
        ;; ── 同 viewport——拼接——调度器内部处理多组
        (-> other
            (assoc :dirty-tiles
                   (into (as-vec (:dirty-tiles this))
                         (as-vec (:dirty-tiles other))))
            (assoc :dirty-transform
                   (into (as-vec (:dirty-transform this))
                         (as-vec (:dirty-transform other)))))))))

(defn- release-layers! [layers]
  (doseq [layer layers]
    (try
      (dispose/dispose-layer layer)
      (catch Throwable t
        (log/error t "dispose cloned layer failed"
                   {:layer-id (:id layer)})))))

(defonce ^:private schedule-task
         (reify CoalescedTask
           (run [_ {:keys [task-id
                           scheduler canvas
                           layers tile-size dirty-tiles dirty-transform
                           image-width image-height current-layer-id
                           viewport viewport-w viewport-h]}]
             (try
               (proto/set-layers! scheduler layers)
               (promise/plet<
                 [result (proto/render! scheduler
                                        {:tile-size        tile-size
                                         :dirty-tiles      dirty-tiles
                                         :dirty-transform  dirty-transform
                                         :image-width      image-width
                                         :image-height     image-height
                                         :current-layer-id current-layer-id
                                         :viewport         viewport
                                         :viewport-w       viewport-w
                                         :viewport-h       viewport-h})]
                 (let [diff-canvas     (:canvas result)
                       clipped-dirties (:dirty-tiles result)]
                   (try
                     (when clipped-dirties
                       (.deleteTiles canvas clipped-dirties))
                     (.mergeCanvas canvas diff-canvas)
                     canvas
                     (finally
                       (.safeClear diff-canvas)))
                   ;; 成功路径释放
                   (release-layers! layers)
                   canvas))

               (catch Throwable t
                 ;; 同步阶段失败——set-layers! 或 plet> 自身构造失败
                 (log/error t "schedule task sync setup failed"
                            {:task-id task-id})
                 (release-layers! layers)
                 nil)))))

(defonce ^:private ^ExecutorService schedule-exec-pool
         (Executors/newSingleThreadExecutor
           (reify ThreadFactory
             (newThread [_ r]
               (doto (Thread. r "krro-schedule-worker")
                 (.setDaemon true))))))


(defonce ^:private ^LatestTaskRunner executor*
         (LatestTaskRunner.
           schedule-task
           (ExecutorServiceAsyncAdapter. schedule-exec-pool)))

(defn executor [] executor*)

(defn submit!
  "提交一次调度任务。

   参数：
     scheduler —— 调度器——显式传入
     layers    —— 待渲染图层——本函数内部克隆
     opts      —— 其余渲染参数（keyword args）
                   :task-id 可选——未提供时生成 UUID

   返回 Promise——任务完成时以渲染结果完成。
   克隆的 layers 由执行器负责释放：
     - 被合并掉的旧任务 → Params/merge 释放
     - 执行完成的任务   → schedule-task/run 释放"

  [task-id scheduler layers & {:keys [ tile-size]
                       :or {tile-size pc/global-tile-size}
                       :as opts}]
  (let [params  (map->Params
                  (assoc opts
                    :task-id   task-id
                    :scheduler scheduler
                    :tile-size tile-size
                    :layers    (mapv clone/clone-layer layers)))]
    (promise/from-completable-future
      (.submit executor* task-id params))))


(defn shutdown!
  "关闭调度执行器——drain 队列并停止线程池。幂等。"
  []
  (.close executor*)
  (.shutdown schedule-exec-pool)
  (try
    (when-not (.awaitTermination schedule-exec-pool 5 TimeUnit/SECONDS)
      (.shutdownNow schedule-exec-pool))
    (catch InterruptedException e
      (.interrupt (Thread/currentThread))
      (.shutdownNow schedule-exec-pool))))
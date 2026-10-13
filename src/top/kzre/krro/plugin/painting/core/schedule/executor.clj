(ns top.kzre.krro.plugin.painting.core.schedule.executor
  "执行环境准备"
  (:require
    [taoensso.timbre :as log]
    [taoensso.tufte :refer [p profile]]
    [top.kzre.krro.canvas.core.layer.render.download :as download]
    [top.kzre.krro.core.util.promise :as promise]
    [top.kzre.krro.plugin.painting.core.layer.clone :as clone]
    [top.kzre.krro.plugin.painting.core.layer.dispose :as dispose]
    [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
    [top.kzre.krro.plugin.painting.core.schedule.protocol :as proto])
  (:import
    (java.util.concurrent
      ExecutorService
      Executors
      ThreadFactory
      TimeUnit)
    (top.kzre.krro.core.util
      CoalescedTask
      ExecutorServiceAsyncAdapter
      LatestTaskRunner
      Mergable)
    (top.kzre.krro.util.tile TiledCanvas)))

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
   changes
   image-width
   image-height
   current-layer-id
   viewport
   viewport-w
   viewport-h]
  Mergable
  (merge [this other]
    (log/debug "merge scheduler task.")
    ;; ── 释放 this 的克隆图层
    (when-let [old-cloned (:layers this)]
      (doseq [layer old-cloned]
        (try
          (dispose/dispose-layer layer)
          (catch Throwable t
            (log/error t "dispose cloned layer failed"
                       {:layer-id (:id layer)})))))
    ;; ── 合并 changes
    (assoc other :changes
                 (into (as-vec (:changes this))
                       (as-vec (:changes other))))))


(defonce ^:private schedule-task
         (reify CoalescedTask
           (run [_ {:keys [task-id
                           scheduler canvas
                           layers tile-size changes
                           image-width image-height current-layer-id
                           viewport viewport-w viewport-h]}]
             ;; 所有权转移给调度器
             (proto/set-layers! scheduler layers)
             (profile
               {:id :run-schedule-task}
               (p :run-schedule-task
                  (promise/plet<
                    [result (proto/render! scheduler
                                           {:tile-size        tile-size
                                            :changes          changes
                                            :image-width      image-width
                                            :image-height     image-height
                                            :current-layer-id current-layer-id
                                            :viewport         viewport
                                            :viewport-w       viewport-w
                                            :viewport-h       viewport-h})]
                    (let [^TiledCanvas
                          diff-canvas (:canvas result)
                          clipped-dirties (:dirty-tiles result)]
                      (try
                        (profile
                          {:id :download-diff-canvas
                           :data {:tile-count
                                  (.size (.getTiles diff-canvas))}}
                          (p :download-diff-canvas
                             (promise/await (download/download!
                                              diff-canvas))))

                        (.deleteTiles canvas clipped-dirties)
                        (.mergeCanvas canvas diff-canvas)
                        canvas
                        (finally
                          (.close diff-canvas)))
                      canvas)))))))

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

  [task-id scheduler layers & {:keys [tile-size]
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
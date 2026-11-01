(ns top.kzre.krro.plugin.painting.core.edit.tool
  "编辑工具抽象, 两个核心抽象 独占工具+双层分发")

(def ^:private tool-data-key* :tool-data)
(def ^:private current-tool-key* :current-tool)
(defn tool-date-key [] tool-data-key*)

(defn current-tool-key [] current-tool-key*)

(defprotocol IToolData
  (cleanup! [_ ctx] "清理工具状态")
  (overlay [_ ctx] "返回叠加层描述")
  (dispatch-event [_ event-map cofx] "根据当前状态进行事件分派，避免单个reframe事件逻辑复杂，nil表示返回使用配置分派")
  )

(defn dispatch-fn
  [static-dispatch-fn]
  (fn [cofx event-v]
    ;; 四个固定参数
    (let [[_ _record-id event-map _frame] event-v
          record (:record cofx)
          current-tool (get record (current-tool-key))
          tool (get record (tool-date-key))]
      (when-let [event-id
                 (or
                   ;; 先尝试基于状态的分派
                   (when (and tool
                              (satisfies? IToolData tool))
                     (dispatch-event tool event-map cofx))
                   ;; 再进行基于配置的分派
                   (static-dispatch-fn current-tool event-map))]
        {:dispatch
         ;; 替换事件ID，后面的参数透传
         (into [event-id] (rest event-v))}
        ))))

(defn cleanup-tool-interceptor
  "返回一个 interceptor，在事件执行前检查并清理旧的工具状态（如果存在）。"
  ([] (cleanup-tool-interceptor nil))
  ([state-clz & {:keys [factory]}]
   {:before
    (fn [context]
      (let [record (get-in context [:coeffects :record])
            tool-data (get record (tool-date-key))]
        (if (and tool-data
                 (or (nil? state-clz) (not (instance? state-clz tool-data))))
          ;; 工具存在，并且不是指定类型
          (do
            (when (satisfies? IToolData tool-data)
              (cleanup! tool-data context))
            (let [new-tool-data (when factory (factory context))]
              (assoc-in context [:coeffects :record  (tool-date-key)] new-tool-data)))
          ;; 无旧数据，直接放行
          context)))}))
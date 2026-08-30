(ns top.kzre.krro.plugin.painting.core.edit.interceptors
  (:require [top.kzre.krro.plugin.painting.core.edit.common :as common]))

(defn cleanup-tool-interceptor
  "返回一个 interceptor，在事件执行前检查并清理旧的工具状态（如果存在）。"
  []
  {:before
   (fn [context]
     (let [record (get-in context [:coeffects :record])
           tool-data (get-in record [:canvas-state :tool-data])]
       (if tool-data
         ;; 存在旧工具数据，执行清理
         (let [new-record (common/cleanup-tool-data! record)]
           (assoc-in context [:coeffects :record] new-record))
         ;; 无旧数据，直接放行
         context)))})
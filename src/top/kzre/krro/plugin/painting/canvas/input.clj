(ns top.kzre.krro.plugin.painting.canvas.input
  "输入源抽象：将硬件事件（鼠标、触摸、数位板）转换为统一的画笔事件。
   每个输入源提供 (start! canvas-node runtime-state) 函数，
   返回清理函数 (stop!) 用于解绑。")

(defprotocol IInputSource
  (start! [this]
    "启动输入源，开始向 callback 发送标准化事件。
     canvas 为平台画布组件（JavaFX Canvas / Android View / HTML Canvas）。
     canvas-id 为画布逻辑标识。
     callback 为 (fn [event])，event 符合 top.kzre.krro.plugin.painting.canvas.event 规范。
     返回清理函数 (fn stop! [])，用于停止输入并解绑监听器。")
  (stop! [this]
    "停止输入源，释放资源。通常由 start! 返回的清理函数调用。"))


(defn- ->event-map
  "将原始坐标转换为内部事件 map。未来可根据输入源类型扩展（压力、倾斜等）。"
  [x y & {:keys [pressure velocity timestamp ]
          :or   {pressure 1
                 velocity 0.5
                 timestamp (System/currentTimeMillis)}}]
  {:x          x
   :y          y
   :pressure   pressure
   :velocity   velocity
   :timestamp  timestamp})

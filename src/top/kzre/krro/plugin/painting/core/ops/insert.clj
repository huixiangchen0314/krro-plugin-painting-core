(ns top.kzre.krro.plugin.painting.core.ops.insert
  "插入新图层，不负责创建图层."
  (:import (top.kzre.krro.plugin.painting.core.project.canvas CanvasData)
           (top.kzre.krro.plugin.painting.core.state CanvasRuntime)))

(defmulti insert-layer!
          "插入新图层,更新项目状态，返回新图层和状态.
          副作用：undo 的记录."
          (fn [layer ^CanvasData data ^CanvasRuntime state] (:type layer)))

(defmethod insert-layer! :raster
 [layer ^CanvasData data ^CanvasRuntime state]
 {:layer layer
  :data data
  :state state})


(defmethod insert-layer! :default
 [layer ^CanvasData data ^CanvasRuntime state]
  {:layer layer
   :data data
   :state state})
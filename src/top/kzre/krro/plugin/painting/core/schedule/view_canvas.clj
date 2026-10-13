(ns top.kzre.krro.plugin.painting.core.schedule.view-canvas
  "视图画布节点"
  (:require
    [top.kzre.krro.core.util.computing-graph :as cg]))

;; 视图画布节点，作为原始图层输入管理合成结果。
(defrecord ViewCanvasNode
  []
cg/INode)
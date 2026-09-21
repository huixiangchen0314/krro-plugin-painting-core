(ns top.kzre.krro.plugin.painting.core.oplog.anchor
  "依赖锚点的挂表，本质还是路径改变"
  (:require
    [top.kzre.krro.core.reframe.core :as rf]
    [top.kzre.krro.plugin.painting.core.store :as store])
  (:import (top.kzre.krro.canvas.vector.anchor Anchor)))


;; 移动锚点
(rf/reg-event-fx
  store/app-id :oplog/anchor-translate
  (fn [cofx [_ canvas-id layer-id
             {:keys []}
             & {:as ctx}]]
    ))

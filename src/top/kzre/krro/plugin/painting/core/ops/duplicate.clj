(ns top.kzre.krro.plugin.painting.core.ops.duplicate
  "图层复制多方法：执行图层深拷贝、插入、侧表维护、刷新与撤销记录。"
  (:require
    [top.kzre.krro.canvas.core.layer.core :as lc]
    [top.kzre.krro.core.hook :as hook]
    [top.kzre.krro.plugin.painting.core.ops.layer :as layer]
    [top.kzre.krro.plugin.painting.core.ops.undo :as undo]
    [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
    [top.kzre.krro.plugin.painting.core.project.raster-layer :as pr]
    [top.kzre.krro.plugin.painting.core.spec :as spec]
    [top.kzre.krro.plugin.painting.core.state :as state])
  (:import (top.kzre.krro.util.tile TiledCanvas)))

;; 分派函数：直接接收图层对象，从中获取类型
(defmulti duplicate-layer!
          "复制图层。根据源图层创建深拷贝，插入到源图层上方，记录撤销并返回新图层信息。"
          (fn [canvas-id layer] (:type layer)))

;; ── 光栅图层 ──────────────────────────────────
(defmethod duplicate-layer! :raster [canvas-id layer]
  (let [layer-id    (:id layer)
        cd          (pc/canvas-data! canvas-id)
        orig-canvas ^TiledCanvas (:canvas layer)
        new-canvas  (doto (TiledCanvas. (.getTileSize orig-canvas)
                                        (.getDefaultPixel orig-canvas))
                      (.shareFrom orig-canvas))
        new-id      (keyword (str (name layer-id) "-copy"))
        ;; 仅更新 id 和 canvas，不删除不存在的 :name
        new-layer   (assoc layer :id new-id :canvas new-canvas)
        layers      (:layers cd)
        path        (lc/find-layer-path layer-id layers)
        insert-path (if path
                      (let [parent (butlast path)
                            idx    (last path)]
                        (conj (vec parent) (inc idx)))
                      [(count layers)])
        {:keys [canvas-data]} (layer/add-layer-at cd insert-path new-layer)]
    (layer/update-project! canvas-id canvas-data)
    (pr/create-raster! new-id canvas-id new-canvas)
    (state/invalidate-canvas-dirty! canvas-id)
    (layer/refresh-canvas-frames! canvas-id)
    (layer/set-selected-layer-id! canvas-id new-id)
    (undo/record-raster-layer-add! canvas-id insert-path new-layer)
    (hook/run-hook! spec/layer-changed-hook-key canvas-id)
    {:layer new-layer :new-layer-id new-id :path insert-path}))

;; ── 矢量图层 ──────────────────────────────────
(defmethod duplicate-layer! :vector [canvas-id layer]
  (let [layer-id (:id layer)
        new-id   (keyword (str (name layer-id) "-copy"))
        ;; 矢量图层纯数据，直接 assoc 新 id 即可，其余字段结构共享
        new-layer (assoc layer :id new-id)
        cd       (pc/canvas-data! canvas-id)
        layers   (:layers cd)
        path     (lc/find-layer-path layer-id layers)
        insert-path (if path
                      (let [parent (butlast path)
                            idx (last path)]
                        (conj (vec parent) (inc idx)))
                      [(count layers)])
        {:keys [canvas-data]} (layer/add-layer-at cd insert-path new-layer)]
    (layer/update-project! canvas-id canvas-data)
    (state/invalidate-canvas-dirty! canvas-id)
    (layer/refresh-canvas-frames! canvas-id)
    (layer/set-selected-layer-id! canvas-id new-id)
    (undo/record-raster-layer-add! canvas-id insert-path new-layer)
    (hook/run-hook! spec/layer-changed-hook-key canvas-id)
    {:layer new-layer :new-layer-id new-id :path insert-path}))
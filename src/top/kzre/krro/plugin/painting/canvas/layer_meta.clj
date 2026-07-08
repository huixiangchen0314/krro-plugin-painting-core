(ns top.kzre.krro.plugin.painting.canvas.layer-meta
  "图层编辑器元数据工具。基于侧表模式，存储在项目原子中。"
  (:require [top.kzre.krro.core.project :as proj]
            [top.kzre.krro.plugin.painting.canvas.project :as canvas-proj])
  (:import [top.kzre.krro.plugin.painting.canvas.project LayerMeta]))

(defn get-meta
  "获取指定图层的元数据实例，若不存在则自动创建默认值。"
  [canvas-id layer-id]
  (canvas-proj/polyfill-layer-meta! canvas-id layer-id))

(defn update-meta!
  "原子更新指定图层的元数据。f 接收当前 LayerMeta 实例，返回新实例。"
  [canvas-id layer-id f]
  (swap! proj/project
         (fn [p]
           (let [path [:krro.painting/layer-meta canvas-id layer-id]
                 current (get-in p path (canvas-proj/polyfill-layer-meta! canvas-id layer-id))]
             (assoc-in p path (f current))))))

;; ── 便捷字段访问 ──────────────────────────────────
(defn locked? [canvas-id layer-id]
  (.locked? (get-meta canvas-id layer-id)))

(defn alpha-locked? [canvas-id layer-id]
  (.alpha-locked? (get-meta canvas-id layer-id)))

(defn expanded? [canvas-id layer-id]
  (.expanded? (get-meta canvas-id layer-id)))

;; ── 便捷字段更新 ──────────────────────────────────
(defn set-locked!
  [canvas-id layer-id v]
  (update-meta! canvas-id layer-id
                (fn [^LayerMeta m]
                  (LayerMeta. (boolean v) (.alpha-locked? m) (.expanded? m)))))

(defn set-alpha-locked!
  [canvas-id layer-id v]
  (update-meta! canvas-id layer-id
                (fn [^LayerMeta m]
                  (LayerMeta. (.locked? m) (boolean v) (.expanded? m)))))

(defn set-expanded!
  [canvas-id layer-id v]
  (update-meta! canvas-id layer-id
                (fn [^LayerMeta m]
                  (LayerMeta. (.locked? m) (.alpha-locked? m) (boolean v)))))
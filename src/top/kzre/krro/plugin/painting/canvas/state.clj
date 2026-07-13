(ns top.kzre.krro.plugin.painting.canvas.state
  "运行时状态：事件、笔刷、缓冲区、累积长度。"
  (:require
    [top.kzre.krro.canvas.core.core :as canv]
    [top.kzre.krro.canvas.core.layer.core :as lc]
    [top.kzre.krro.core.frame :as frame]
    [top.kzre.krro.plugin.painting.project.canvas :as pc]
    [top.kzre.krro.plugin.painting.spec :as spec])
  (:import
   (top.kzre.krro.canvas.core Arrays)
   (top.kzre.krro.plugin.painting.project.canvas CanvasData)))

(defn frames-with-canvas-id
  "返回所有显示指定画布的 Frame。"
  [canvas-id]
  (frame/frames-with-param spec/canvas-id-key canvas-id))

(defrecord CanvasRuntime
  [new-events         ;; 本帧新事件
   all-events         ;; 整个笔画事件序列（提交用）
   ^floats preview-buffer     ;; 预览缓冲区
   ^floats layer-buffer       ;; 光栅图层原始数据备份（笔画开始时拷贝）
   selected-layer-id  ;; 当前选中的图层id
   stroke-length      ;; 已预览像素长度（用作 start-dist）
   ])

(defn default-state
  [buffer-size]
  {:new-events     []
   :all-events     []
   :preview-buffer (float-array buffer-size)
   :layer-buffer   (float-array buffer-size)
   :selected-layer-id nil
   :stroke-length  0.0})

(defn make-state
  [width height]
  (let [n (* width height 4)]
    (map->CanvasRuntime
      (default-state n))))

(defonce canvas-runtimes (atom {}))

(defn canvas-runtime [canvas-id]
  (get @canvas-runtimes canvas-id))

(defn selected-layer-id [canvas-id]
  (when-let [rt (canvas-runtime canvas-id)]
    (:selected-layer-id rt)))

(defn selected-layer! [canvas-id]
  (when-let [rt (pc/canvas-data! canvas-id)]
    (when-let [lid (selected-layer-id canvas-id)]
      (let [ls (:layers rt)]
        (lc/find-layer lid ls)))))

(defn selected-layer-type [canvas-id]
  (when-let [lid (selected-layer-id canvas-id)]
    (let [layers (pc/layers-by-id canvas-id)]
      (when-let [l (lc/find-layer lid layers)]
        (:type l)))))

(defn preview-buffer [^CanvasRuntime rt] (:preview-buffer rt))
(defn layer-buffer [^CanvasRuntime rt] (:layer-buffer rt))

(defn preview-buffer-by-id [canvas-id]
  (when-let [^CanvasRuntime rt (canvas-runtime canvas-id)]
    (:preview-buffer rt)))

(defn layer-buffer-by-id [canvas-id]
  (when-let [^CanvasRuntime rt (canvas-runtime canvas-id)]
    (:layer-buffer rt)))

(declare ensure-runtime!)

(defn render-canvas!
  "渲染当前画布所有图层到目标数组。"
  ([canvas-id]
   (let [rt (ensure-runtime! canvas-id)
         preview (:preview-buffer rt)]
     (render-canvas! canvas-id preview)))
  ([canvas-id ^floats dest]
   (when-let [cd (pc/canvas-data! canvas-id)]
     (let [layers (:layers ^CanvasData cd)
           w (:width ^CanvasData cd)
           h (:height ^CanvasData cd)]
       (Arrays/fill dest (float 0.0))
       (canv/render-layers! layers dest w h)))))

(defn ensure-runtime!
  ([canvas-id]
   (ensure-runtime! canvas-id 800 600))
  ([canvas-id w h]
   (or (canvas-runtime canvas-id)
       (let [_cd (pc/ensure-canvas-data! canvas-id w h)
             rt (make-state w h)
             preview (:preview-buffer rt)]
         (render-canvas! canvas-id preview)
         (swap! canvas-runtimes assoc canvas-id rt)
         rt))))

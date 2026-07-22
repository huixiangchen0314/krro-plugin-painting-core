(ns top.kzre.krro.plugin.painting.core.state
  "运行时状态：事件、笔刷、缓冲区、累积长度。"
  (:require
   [top.kzre.krro.canvas.core.core :as canv]
   [top.kzre.krro.canvas.core.layer.core :as lc]
   [top.kzre.krro.core.frame :as frame]
   [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
   [top.kzre.krro.plugin.painting.core.spec :as spec])
  (:import
   [java.util Arrays]
   (top.kzre.krro.plugin.painting.core State)
   (top.kzre.krro.plugin.painting.core.project.canvas CanvasData)))

(defn frames-with-canvas-id
  "返回所有显示指定画布的 Frame。"
  [canvas-id]
  (frame/frames-with-param spec/canvas-id-key canvas-id))

(defrecord CanvasRuntime
  [^floats preview-buffer     ;; 预览缓冲区
   layer-backup
   current-tool                                                     ;; 当前选择的工具.
   dirty-tiles
   ])

(defn default-state
  [buffer-size]
  {:preview-buffer    (float-array buffer-size)
   :layer-backup      nil
   :current-tool      nil
   :dirty-tiles []
   })

(defn make-state
  [width height]
  (let [n (* width height 4)]
    (map->CanvasRuntime
      (default-state n))))

(defn layer-backup [^CanvasRuntime rt] (:layer-backup rt))


(defonce canvas-runtimes (atom {}))

(defn canvas-runtime [canvas-id]
  (get @canvas-runtimes canvas-id))

(defn selected-layer-id [canvas-id]
  (pc/selected-layer-id canvas-id))

(defn selected-layer! [canvas-id]
  (when-let [rt (pc/canvas-data! canvas-id)]
    (when-let [lid (pc/selected-layer-id canvas-id)]
      (let [ls (:layers rt)]
        (lc/find-layer lid ls)))))

(defn selected-layer-type [canvas-id]
  (when-let [lid (selected-layer-id canvas-id)]
    (let [layers (pc/layers-by-id canvas-id)]
      (when-let [l (lc/find-layer lid layers)]
        (:type l)))))

(defn current-tool [canvas-id]
  (when-let [rt (canvas-runtime canvas-id)]
    (:current-tool rt)))

(defn set-current-tool! [canvas-id new-tool]
  (swap! canvas-runtimes assoc-in [canvas-id :current-tool] new-tool))

(defn preview-buffer [^CanvasRuntime rt] (:preview-buffer rt))
(defn layer-buffer [^CanvasRuntime rt] (:layer-buffer rt))

(defn preview-buffer-by-id [canvas-id]
  (when-let [^CanvasRuntime rt (canvas-runtime canvas-id)]
    (:preview-buffer rt)))

(defn layer-buffer-by-id [canvas-id]
  (when-let [^CanvasRuntime rt (canvas-runtime canvas-id)]
    (:layer-buffer rt)))

(defn set-layer-backup! [canvas-id new-backup]
  (swap! canvas-runtimes assoc-in [canvas-id :layer-backup] new-backup))

(defn add-dirty-tiles!
  "将脏瓦片集合合并到全局运行时状态。"
  [canvas-id tiles]
  (swap! canvas-runtimes update-in [canvas-id :dirty-tiles] into tiles))

(declare ensure-runtime!)

(defn invalidate-canvas-dirty! [canvas-id]
  (swap! canvas-runtimes assoc-in [canvas-id :dirty-tiles] nil))

(defn render-canvas!
  "渲染当前画布所有图层到目标数组。
   dirty-tiles 语义：
     nil        → 全图刷新（清除整个缓冲区并重绘所有图层）
     非空集合   → 只清除脏瓦片对应区域，然后重绘所有图层（目前仍为全图层合成，后续可优化为局部合成）
     空集合     → 无脏区域，直接返回，不做任何操作（不清除、不渲染、不修改脏标记）
   渲染完成后将 dirty-tiles 重置为空集合（表示已同步）。"
  ([canvas-id]
   (let [rt (ensure-runtime! canvas-id)
         preview (:preview-buffer rt)]
     (render-canvas! canvas-id preview)))
  ([canvas-id ^floats dest]
   (when-let [cd (pc/canvas-data! canvas-id)]
     (let [layers (:layers ^CanvasData cd)
           w (:width ^CanvasData cd)
           h (:height ^CanvasData cd)
           rt (canvas-runtime canvas-id)
           dirty-tiles (:dirty-tiles rt)
           tile-size pc/global-tile-size]
       (cond
         ;; 全图刷新
         (nil? dirty-tiles)
         (do
           (Arrays/fill dest (float 0.0))
           (canv/render-layers! layers dest w h)
           (swap! canvas-runtimes assoc-in [canvas-id :dirty-tiles] #{}))

         ;; 增量更新（可能为空集合）
         (empty? dirty-tiles)
         nil   ;; 无脏区域，直接返回

         ;; 有脏瓦片
         :else
         (do
           ;; 调用 Java 方法高效清空脏瓦片区域
           (State/clearDirtyTiles dest w h dirty-tiles tile-size)
           (canv/render-layers! layers dest w h
                                :dirty-tiles dirty-tiles
                                :tile-size tile-size)
           (swap! canvas-runtimes assoc-in [canvas-id :dirty-tiles] #{})))))))

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

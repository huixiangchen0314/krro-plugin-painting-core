(ns top.kzre.krro.plugin.painting.core.state
  "运行时状态：事件、笔刷、缓冲区、累积长度。"
  (:require
    [top.kzre.krro.canvas.core.core :as canv]
    [top.kzre.krro.canvas.core.layer.core :as lc]
    [top.kzre.krro.core.core :as kcc]
    [top.kzre.krro.core.frame :as frame]
    [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
    [top.kzre.krro.plugin.painting.core.spec :as spec])
  (:import
    (java.util Collection)
    (top.kzre.krro.brush Stroke)
    (top.kzre.krro.plugin.painting.core.project.canvas CanvasData)
    (top.kzre.krro.util.math KMath)
    (top.kzre.krro.util.tile CanvasUtils TiledCanvas)))

(defn frames-with-canvas-id
  "返回所有显示指定画布的 Frame。"
  [canvas-id]
  (frame/frames-with-param spec/canvas-id-key canvas-id))

(defn rerender-frame-with-canvas-id! [canvas-id]
  (doseq [f (frames-with-canvas-id canvas-id)]
    (kcc/rerender! f)))

;; TODO 把tool重构成proj
;; TODO 渲染缓存
(defrecord CanvasState
  [^TiledCanvas preview-canvas                              ;; 预览画布
   selected-layer-id                                        ;; 当前选中图层id
   selected-layer-ids                                       ;; 当前选中的所有图层id.
   layer-backup                                             ;; 图层备份数据
   current-tool                                             ;; 当前选择工具
   dirty-tiles                                              ;; 画布脏tile
   ^Stroke stroke                                           ;; 当前笔刷笔触
   layer-transform                                          ;; 当前图层正变换仿射矩阵
   layer-transform-inv                                      ;; 当前图层逆变换仿射矩阵
   cursor-position                                          ;; 最新光标位置
   rendering?                                               ;; 是否正在执行渲染
   pending-render?                                          ;; 是否有等待中的渲染请求
   uploading?                                               ;; 是否正在上传画布
   pending-upload?                                          ;; 是否有等待中的上传请求
   ])

(defn make-state []
  (map->CanvasState {:preview-canvas   (TiledCanvas. pc/global-tile-size )
                     :selected-layer-id nil
                     :selected-layer-ids nil
                     :layer-backup     nil
                     :current-tool     nil
                     :dirty-tiles      #{}
                     :stroke nil
                     :layer-transform (KMath/mat2dIdentity)
                     :layer-transform-inv (KMath/mat2dIdentity)
                     :cursor-position {:x 0 :y 0}
                     :rendering?     false          ;; 是否正在执行渲染
                     :pending-render? false         ;; 是否有等待中的渲染请求
                     :uploading? false
                     :pending-upload? false
                     }))

(defn layer-backup [^CanvasState rt] (:layer-backup rt))



(defonce canvas-runtimes (atom {}))

(defn canvas-runtime [canvas-id]
  (get @canvas-runtimes canvas-id))

(defn current-layer-id [canvas-id]
  (pc/current-layer-id canvas-id))

(defn current-layer! [canvas-id]
  (when-let [cd (pc/canvas-data! canvas-id)]
    (when-let [lid (pc/current-layer-id canvas-id)]
      (let [ls (:layers cd)]
        (lc/find-layer lid ls)))))



(defn pure-current-layer!
  "获取干净的当前图层.当前图层是脏的时候，返回备份图层，否则返回项目图层数据"
  [canvas-id]
  (when-let [rt (canvas-runtime canvas-id)]
    (if (seq  (:dirty-tiles rt))
     (:layer-backup rt)
     (current-layer! canvas-id))))

(defn current-tool [canvas-id]
  (when-let [rt (canvas-runtime canvas-id)]
    (:current-tool rt)))

(defn set-current-tool! [canvas-id new-tool]
  (swap! canvas-runtimes assoc-in [canvas-id :current-tool] new-tool))

(defn preview-canvas [^CanvasState rt]
  (:preview-canvas rt))

(defn preview-canvas-by-id [canvas-id]
  (when-let [^CanvasState rt (canvas-runtime canvas-id)]
    (:preview-canvas rt)))

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
  "渲染当前画布所有图层到目标画布（TiledCanvas）。
   dirty-tiles 语义：
     nil        → 全图刷新（清空整个画布并重绘所有图层）
     非空集合   → 只清除脏瓦片对应区域，然后重绘所有图层
     空集合     → 无脏区域，直接返回，不做任何操作
   渲染完成后将 dirty-tiles 重置为空集合（表示已同步）。"
  ([canvas-id]
   (let [rt (ensure-runtime! canvas-id)
         canvas (:preview-canvas rt)]
     (render-canvas! canvas-id canvas)))
  ([canvas-id ^TiledCanvas dest]
   (when-let [cd (pc/canvas-data! canvas-id)]
     (let [layers (:layers ^CanvasData cd)
           w (:width ^CanvasData cd)
           h (:height ^CanvasData cd)
           tile-size (.getTileSize dest)
           rt (canvas-runtime canvas-id)
           dirty-tiles (:dirty-tiles rt)]
       (cond
         ;; 全图刷新：清除画布所有瓦片，然后重绘
         (nil? dirty-tiles)
         (do
           (.clear dest)
           (canv/render-layers! layers dest w h)
           (swap! canvas-runtimes assoc-in [canvas-id :dirty-tiles] #{}))

         ;; 增量更新：脏瓦片为空集合，直接返回
         (empty? dirty-tiles)
         nil

         ;; 有脏瓦片：先删除脏瓦片（相当于清空该区域），再重绘所有图层（未来可优化为按脏瓦片裁剪）
         :else
         (do
           ;; 利用 TiledCanvas 的 deleteTiles 高效清除脏瓦片区域
           (.deleteTiles dest ^Collection dirty-tiles)
           (canv/render-layers! layers dest w h
                                :dirty-tiles (CanvasUtils/clipTiles dirty-tiles tile-size w h)
                                :tile-size pc/global-tile-size)
           (swap! canvas-runtimes assoc-in [canvas-id :dirty-tiles] #{})))))))

(defn ensure-runtime!
  ([canvas-id]
   (ensure-runtime! canvas-id 800 600))
  ([canvas-id w h]
   (or (canvas-runtime canvas-id)
       (let [_cd (pc/ensure-canvas-data! canvas-id w h)
             rt (make-state)
             canvas (:preview-canvas rt)]
         (render-canvas! canvas-id canvas)
         (swap! canvas-runtimes assoc canvas-id rt)
         rt))))
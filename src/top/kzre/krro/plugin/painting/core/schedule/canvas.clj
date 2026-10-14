(ns top.kzre.krro.plugin.painting.core.schedule.canvas
  (:require
   [top.kzre.krro.core.util.computing-graph :as cg]
   [top.kzre.krro.plugin.painting.core.schedule.protocol :as proto]
   [top.kzre.krro.core.util.promise :as promise]
   [top.kzre.krro.plugin.painting.core.schedule.layer-impl :as layer-impl]
   [top.kzre.krro.canvas.core.layer.render.download :as download])
  (:import
    (top.kzre.krro.core.util.computing_graph INode)
    (top.kzre.krro.util.math KMath)
    (top.kzre.krro.util.tile TiledCanvas)))

(defrecord MergeCanvasNode
  [^TiledCanvas backdrop
   layer-id]

  cg/INode
  (node-id [_] (keyword (str layer-id "-backdrop-merged")))
  (dependencies [_] [:context layer-id])
  (compute [this [{:keys [view-dirty-tiles image-dirty-tiles]} layer]]
    (let [transform (proto/transform layer)]
      (when-not (KMath/mat2dIsIdentity transform)
        (throw (ex-info "layer transform must be identity" {:node (cg/node-id this)}))))
    (let [canvas (doto (proto/canvas layer)
                   (.keepTiles image-dirty-tiles))
          merged (doto backdrop
                   (.deleteTiles view-dirty-tiles)
                   (.mergeCanvas canvas))]
      ;; 想持久化持有瓦片, 就必须把瓦片下载下来，不然会泄漏
      ;; 这个节点是回退路径，一般是 cpu软件渲染，或者gpu拓展是非差的节点使用
      (-> (download/download! merged)
          (promise/fmap
            (fn [_]
              (layer-impl/wrap-layer (cg/node-id this) merged))))))
  (release [_ _]
    ;; backdrop 是借用，不释放
    ))

(defn make-merge-canvas-node
  "构造 MergeCanvasNode。

   把差分画布合并到 backdrop，并下载结果瓦片。

   <b>必须下载</b>——backdrop 跨帧持久持有；不下载则瓦片以 GPU
   形态被长期占用，非托管资源无法回收，导致槽位峰值。

   回退路径——用于 CPU 软件渲染，或 GPU 侧的非常规节点。GL 主
   路径直接合并纹理，不走此节点。

   backdrop 由调度器持有，本节点借用不释放。"
  [^TiledCanvas backdrop ^INode node]
  (->MergeCanvasNode backdrop (cg/node-id node)))
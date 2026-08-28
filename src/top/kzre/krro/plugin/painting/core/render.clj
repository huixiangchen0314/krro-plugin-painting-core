(ns top.kzre.krro.plugin.painting.core.render
  (:require
   [clojure.core.async :as async]
   [top.kzre.krro.canvas.core.core :as canv]
   [top.kzre.krro.core.hook :as hook]
   [top.kzre.krro.plugin.painting.core.layer.dispose :as dispose]
   [top.kzre.krro.plugin.painting.core.project.canvas :as pc])
  (:import
   [java.util Collection]
   [top.kzre.krro.util.tile CanvasUtils TiledCanvas]))

(defn render-canvas
  [layers width height dirty-tiles ^TiledCanvas dest]
  (let [tile-size (.getTileSize dest)]
    (cond
      ;; 全图刷新：清除画布所有瓦片，然后重绘
      (nil? dirty-tiles)
      (do
        (.clear dest)
        (canv/render-layers! layers dest width height))

      ;; 增量更新：脏瓦片为空集合，直接返回
      (empty? dirty-tiles) nil

      ;; 有脏瓦片：先删除脏瓦片（相当于清空该区域），再重绘所有图层（未来可优化为按脏瓦片裁剪）
      :else
      (do
        ;; 利用 TiledCanvas 的 deleteTiles 高效清除脏瓦片区域
        (.deleteTiles dest ^Collection dirty-tiles)
        (canv/render-layers! layers dest width height
                             :dirty-tiles (CanvasUtils/clipTiles dirty-tiles tile-size width height)
                             :tile-size pc/global-tile-size)))))

(defonce render-channels (atom {}))

(defn get-render-chan [record-id]
  (or (get @render-channels record-id)
      (let [ch (async/chan 1)]  ;; 缓冲 1，自动丢弃旧值
        (swap! render-channels assoc record-id ch)
        (async/go-loop []
                       (let [req (async/<! ch)]
                         (when req
                           (let [{:keys [layers width height dirty-tiles canvas]} req]
                             ;; 执行渲染（只处理最新的请求，因为缓冲只有1，旧请求会被覆盖）
                             (render-canvas layers width height dirty-tiles canvas)
                             ;; 释放图层
                             (doseq [l layers]
                               (dispose/dispose-layer l))
                             (hook/run-hook! :krro.painting/after-render-canvas-hook record-id canvas width height)
                             ;; 继续循环
                             (recur)))))
        ch)))


(defn close-render-chan [record-id]
  (when-let [ch (get @render-channels record-id)]
    (async/close! ch)
    (swap! render-channels dissoc record-id)))
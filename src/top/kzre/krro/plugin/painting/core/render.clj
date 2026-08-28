(ns top.kzre.krro.plugin.painting.core.render
  (:require
    [clojure.core.async :as async]
    [top.kzre.krro.canvas.core.core :as canv]
    [top.kzre.krro.core.hook :as hook]
    [top.kzre.krro.plugin.painting.core.layer.dispose :as dispose]
    [top.kzre.krro.plugin.painting.core.layer.clone :as clone]
    [top.kzre.krro.plugin.painting.core.project.canvas :as pc])
  (:import
    [java.util Collection]
    [top.kzre.krro.util.tile CanvasUtils TiledCanvas]))

(defn render-canvas
  [layers width height dirty-tiles ^TiledCanvas dest]
  (let [tile-size (.getTileSize dest)]
    (cond
      (nil? dirty-tiles)
      (do
        (.clear dest)
        (canv/render-layers! layers dest width height))

      (empty? dirty-tiles) nil

      :else
      (do
        (.deleteTiles dest ^Collection dirty-tiles)
        (canv/render-layers! layers dest width height
                             :dirty-tiles (CanvasUtils/clipTiles dirty-tiles tile-size width height)
                             :tile-size pc/global-tile-size)))))

(defonce render-channels (atom {}))
(defonce pending-requests (atom {}))   ;; record-id -> {:layers, :width, :height, :canvas, :dirty-tiles}

(defn get-render-chan [record-id]
  (or (get @render-channels record-id)
      (let [ch (async/chan 1)]  ;; 缓冲 1，确保只有一个信号在等待
        (swap! render-channels assoc record-id ch)
        (async/go-loop []
          (let [signal (async/<! ch)]
            (when signal
              (let [record-id (:record-id signal)
                    req (get @pending-requests record-id)]
                (when req
                  ;; 清除 pending，防止重复处理
                  (swap! pending-requests dissoc record-id)
                  (let [{:keys [layers width height canvas dirty-tiles]} req]
                    (render-canvas layers width height dirty-tiles canvas)
                    (doseq [l layers]
                      (dispose/dispose-layer l))
                    (hook/run-hook! :krro.painting/after-render-canvas-hook record-id canvas width height))))
              (recur))))
        ch)))

(defn request-render!
  [record-id layers width height canvas dirty-tiles]
  (swap! pending-requests
         (fn [current]
           (let [old (get current record-id)]
             ;; 释放旧的 pending 图层（如果存在）
             (when old
               (doseq [l (:layers old)]
                 (dispose/dispose-layer l)))
             ;; 克隆新的图层（每次都重新克隆）
             (let [cloned (mapv clone/clone-layer layers)
                   merged-dirty (if old
                                  (into (:dirty-tiles old) (or dirty-tiles #{}))
                                  (or dirty-tiles #{}))
                   final-dirty
                   ;; 宽度和高度改变了，强制进行全量渲染.
                   (if (or (not= width (:width old))
                           (not= height (:height old)))
                     nil
                     merged-dirty)]
               {record-id {:layers cloned
                           :width width
                           :height height
                           :canvas canvas
                           :dirty-tiles final-dirty}}))))
  ;; 发送信号
  (let [ch (get-render-chan record-id)]
    (async/put! ch {:record-id record-id})))

(defn shutdown-render! [record-id]
  (when-let [ch (get @render-channels record-id)]
    (async/close! ch)
    (swap! render-channels dissoc record-id)
    (swap! pending-requests dissoc record-id)))
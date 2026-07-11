;; ── layer-browser.clj ────────────────────────────────
(ns top.kzre.krro.plugin.painting.ui.layer-browser
  "图层面板：标题、图层列表（自定义组件）和工具栏。"
  (:require
    [top.kzre.krro.plugin.painting.canvas.layer :as layer]
    [top.kzre.krro.plugin.painting.canvas.layer-undo :as layer-undo]
    [top.kzre.krro.plugin.painting.canvas.state :as state]))

(defn layer-panel-vnode [canvas-id f]
  [:block {:class "layer-browser" :direction :vertical}
   [:text {:class "layer-browser-title" :content "Layers"}]
   ;; 使用自定义图层列表组件
   [:krro.painting/layer-list {:krro.painting/canvas-id canvas-id}]
   [:block {:class "layer-toolbar" :direction :horizontal}
    [:button {:class "layer-toolbar-button" :content "＋"
              :on-click (fn [_] (layer-undo/add-raster-layer-over-selected-undo! canvas-id))}]
    [:button {:class "layer-toolbar-button" :content "×"
              :on-click (fn [_]
                          (when-let [path (layer/selected-layer-path canvas-id)]
                            (layer-undo/remove-layer-at-undo! canvas-id path)))}]
    [:button {:class "layer-toolbar-button" :content "▣"
              :on-click (fn [_]
                          (when-let [sid (state/selected-layer-id canvas-id)]
                            (layer-undo/duplicate-layer-undo! canvas-id sid)))}]]])
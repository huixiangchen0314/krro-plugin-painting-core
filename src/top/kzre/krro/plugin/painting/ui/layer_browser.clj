(ns top.kzre.krro.plugin.painting.ui.layer-browser
  (:require
    [top.kzre.krro.plugin.painting.canvas.layer :as layer]
    [top.kzre.krro.plugin.painting.canvas.project :as canv-proj]))

(defn layer-panel-vnode [canvas-id f]
  ;; 返回图层列表的 EDN 向量，可被 layout-fn 直接使用
  (let [layers      (canv-proj/layers-by-id canvas-id)
        selected-id (layer/get-selected-layer-id f)]
    [:block {:direction :vertical :style {:padding 8}}
     [:text {:content "Layers" :style {:font-weight "bold" :font-size 14}}]
     (if (seq layers)
       (vec (map-indexed (fn [idx l]
                           (let [lid (:id l) name (or (:name l) (str lid))]
                             [:block {:key lid :style {:padding 2}}
                              [:text {:content name :style {:color (if (= lid selected-id) "yellow" "lightgray")}
                                      :on-click (fn [_] (layer/set-selected-layer-id! f lid))}]
                              [:button {:content "×" :on-click (fn [_] (layer/remove-layer! f canvas-id lid))}]]))
                         layers))
       [:text {:content "No layers"}])]))
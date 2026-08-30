(ns top.kzre.krro.plugin.painting.core.edit.move
  (:require
    [top.kzre.krro.canvas.core.layer.util :as util]
    [top.kzre.krro.core.custom :as custom]
    [top.kzre.krro.core.reframe :as rf]
    [top.kzre.krro.plugin.painting.core.edit.common :as common]
    [top.kzre.krro.plugin.painting.core.edit.interceptors :refer [cleanup-tool-interceptor]]
    [top.kzre.krro.plugin.painting.core.layer.tiles :as tiles]
    [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
    [top.kzre.krro.plugin.painting.core.store :as store])
  (:import
    (top.kzre.krro.canvas.core.layer LayerUtils)
    (top.kzre.krro.util.math KMath)))

(custom/defcustom :krro.painting/move-tool-speed
                  1.0
                  :type :number
                  :group :krro.painting/edit
                  :doc "移动工具的移动速度配置, 1.0 为像素同比速度.")

(custom/defcustom :krro.painting/move-tool-dead-zone
                  0.5
                  :type :number
                  :group :krro.painting/edit
                  :doc "移动工具的死区（像素），位移小于此值时忽略移动。")


(defn layer-transform
  [layer parent-transform]
  (KMath/mat2dMul (util/compose-local-transform layer) parent-transform))

(defn world-tiles
  [layer parent-transform]
  (when-let [local-tiles (tiles/layer-tiles layer pc/global-tile-size)]
    (let [trans (layer-transform layer parent-transform)]
      (LayerUtils/transformTiles local-tiles pc/global-tile-size trans))))

(rf/reg-event-fx
  store/app-id :move-tool/press
  [(cleanup-tool-interceptor)]
  (fn [cofx [_ _record-id cursor-x cursor-y]]
    (let [record (:record cofx)
          cd (:canvas-data record)]
      (if-let [current-layer-id (:current-layer-id cd)]
        (let [layers (:layers cd)]
          (when-let [path (util/find-layer-path current-layer-id layers)]
            (let [layer (util/find-layer-by-path path layers)]
              {:record (assoc-in record [:canvas-state :tool-data]
                                 {:move/init-cursor-x cursor-x
                                  :move/init-cursor-y cursor-y
                                  :move/original-layer layer
                                  :move/last-layer layer
                                  :move/parent-transform (util/parent-transform path layers)
                                  :move/active? true})})))
        {:fx [:warn "No active layer."]}))))

(rf/reg-event-fx
  store/app-id :move-tool/drag
  (fn [cofx [_ record-id frame cursor-x cursor-y]]
    (let [record (:record cofx)
          tool-data (get-in record [:canvas-state :tool-data])]
      (when (:move/active? tool-data)
        (let [speed      (custom/get-custom :krro.painting/move-tool-speed frame)
              dead-zone  (custom/get-custom :krro.painting/move-tool-dead-zone frame)
              original-layer (:move/original-layer tool-data)
              dx (* speed (- cursor-x (:move/init-cursor-x tool-data)))
              dy (* speed (- cursor-y (:move/init-cursor-y tool-data)))]
          (if (and (< (Math/abs (double dx)) dead-zone)
                   (< (Math/abs (double dy)) dead-zone))
            ;; 移动过小，什么也不做
            nil
            (let [new-x (+ (or (:x original-layer) 0.0) dx)
                  new-y (+ (or (:y original-layer) 0.0) dy)
                  last-layer (:move/last-layer tool-data)
                  new-layer (assoc original-layer :x new-x :y new-y)
                  parent-transform (:move/parent-transform tool-data)
                  dirty-tiles
                  (if-let [tiles1 (world-tiles last-layer parent-transform)]
                    (if-let [tiles2 (world-tiles new-layer parent-transform)]
                      (into (set tiles1) tiles2)
                      nil)
                    nil)]
              {:record (-> record
                           (update-in [:canvas-data :layers]
                                      (fn [layers] (util/replace-layer new-layer layers)))
                           (update-in [:canvas-data :tool-data]
                                      (fn [data] (assoc data :move/last-layer new-layer))))
               :fx [[:render-canvas record-id dirty-tiles]]})))))))

(rf/reg-event-fx
  store/app-id :move-tool/release
  (fn [cofx [_ record-id]]
    (let [record (:record cofx)
          tool-data (get-in record [:canvas-state :tool-data])

          active? (:move/active? tool-data)]
      (when active?
        (let [trans (layer-transform (:move/last-layer tool-data)
                                     (:move/parent-transform tool-data))
              trans-inv (KMath/mat2dInv trans)]
          {:record (-> record
                       (common/cleanup-tool-data!)
                       (assoc-in [:canvas-state :layer-transform] trans)
                       (assoc-in [:canvas-state :layer-transform-inv] trans-inv))
           :fx [:record-canvas-edited record-id]})))))
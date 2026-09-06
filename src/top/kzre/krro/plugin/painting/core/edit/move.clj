(ns top.kzre.krro.plugin.painting.core.edit.move
  (:require
   [top.kzre.krro.canvas.core.layer.util :as util]
   [top.kzre.krro.core.custom :as custom]
   [top.kzre.krro.core.reframe :as rf]
   [top.kzre.krro.plugin.painting.core.edit.interceptors :refer [cleanup-tool-interceptor]]
   [top.kzre.krro.plugin.painting.core.edit.protocol :as p]
   [top.kzre.krro.plugin.painting.core.layer.tiles :as tiles]
   [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
   [top.kzre.krro.plugin.painting.core.store :as store]
   [top.kzre.krro.plugin.painting.core.viewport :as vp])
  (:import
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

(defrecord MoveState [init-cursor-x init-cursor-y original-layer last-layer parent-transform]
  p/IToolData
  (dispatch-event [_ event-map] )

  (cleanup! [_ _]
    nil)
  (overlay [_ _]
    nil))


(rf/reg-event-fx
  store/app-id :move-tool/press
  [(cleanup-tool-interceptor)]
  (fn [cofx [_ _ {:keys [x y] } frame]]
    (let [record (:record cofx)
          cd (:canvas-data record)]
      (if-let [current-layer-id (:current-layer-id cd)]
        (let [layers (:layers cd)]
          (when-let [path (util/find-layer-path current-layer-id layers)]
            (let [viewport (vp/get-viewport frame)
                  p  (vp/screen->logic viewport x y)
                  layer (util/find-layer-by-path path layers)
                  parent-transform (util/parent-transform path layers)]
              {:record (assoc-in record [:canvas-state :tool-data]
                                 (->MoveState (:x p) (:y p) layer layer parent-transform))})))
        {:fx [:warn "No active layer."]}))))

(rf/reg-event-fx
  store/app-id :move-tool/drag
  (fn [cofx [_ record-id {:keys [x y]} frame]]
    (let [record (:record cofx)
          state (get-in record [:canvas-state :tool-data])]
      (when (instance? MoveState state)
        (let [speed      (custom/get-custom :krro.painting/move-tool-speed frame)
              dead-zone  (custom/get-custom :krro.painting/move-tool-dead-zone frame)
              {:keys [parent-transform original-layer]} state
              viewport (vp/get-viewport frame)
              p  (vp/screen->logic viewport x y)
              dx (* speed (- (:x p) (:init-cursor-x state)))
              dy (* speed (- (:y p) (:init-cursor-y state)))]
          (if (and (< (Math/abs (double dx)) dead-zone)
                   (< (Math/abs (double dy)) dead-zone))
            ;; 移动过小，什么也不做
            nil
            (let [new-x (+ (or (:x original-layer) 0.0) dx)
                  new-y (+ (or (:y original-layer) 0.0) dy)
                  last-layer (:last-layer state)
                  new-layer (assoc original-layer :x new-x :y new-y)
                  dirty-tiles
                  (if-let [tiles1 (tiles/layer-tiles last-layer pc/global-tile-size)]
                    (if-let [tiles2 (tiles/layer-tiles new-layer pc/global-tile-size)]
                      (into tiles1 tiles2)
                      nil)
                    nil)
                  layer-transform (KMath/mat2dMul parent-transform (util/compose-local-transform new-layer))]
              {:record (-> record
                           (update-in [:canvas-data :layers]
                                      (fn [layers] (util/replace-layer new-layer layers)))
                           (assoc-in [:canvas-data :tool-data :last-layer] new-layer))
               :fx [[:render-canvas record-id dirty-tiles layer-transform]]})))))))

(rf/reg-event-fx
  store/app-id :move-tool/release
  (fn [cofx [_ record-id _ _]]
    (let [record (:record cofx)
          state (get-in record [:canvas-state :tool-data])]
      (when (instance? MoveState state)
        {:fx [[:record-canvas-edited record-id]]}))))
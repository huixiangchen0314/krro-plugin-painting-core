(ns top.kzre.krro.plugin.painting.core.project.canvas
  "画布数据，负责定义总体画布结构，并提供多方法供图层拓展."
  (:require
    [top.kzre.krro.canvas.core.layer.core :as lc]
    [top.kzre.krro.core.core :as kcc]
    [top.kzre.krro.core.project :as proj]
    [top.kzre.krro.core.rdb :refer [defschema]])
  (:import
    (java.util UUID)))

;; 全局瓦片大小 256x256
(defonce global-tile-size 256 )

;; 定义记录以便自定义编解码.
(defrecord CanvasData [id width height layers current-layer-id])

(defschema :krro.painting/canvas
               :primary-key :id
               :not-null [:id :width :height :layers]
               :defaults {:layers []})

(defn make-test-one-vanish-point-perspective-layer []
  (let [layer-id (keyword (str "layer-" (UUID/randomUUID)))]
    {:id           layer-id
     :type         :perspective
     :name         "Test One-Point Perspective"
     :opacity      1.0
     :blend-mode   :normal
     :visible     true
     :transform    [1 0 0 1 0 0]
     :antialiased  true
     :camera {:position [500.0 500.0 500.0]
              :target   [500.0 500.0   0.0]
              :up       [  0.0   0.0   1.0]
              :fov      45.0
              :near     1.0}
     :lines []
     :grids []
     :elements [{:type :one-point-perspective
                 :vp   [400.0 300.0]
                 :radial-lines 12}]}))

(defn make-test-two-vanish-point-perspective-layer []
  (let [layer-id (keyword (str "layer-" (UUID/randomUUID)))]
    {:id           layer-id
     :type         :perspective
     :name         "Test Two-Point Perspective"
     :opacity      1.0
     :blend-mode   :normal
     :visible     true
     :transform    [1 0 0 1 0 0]
     :antialiased  true
     :camera {:position [500.0 500.0 500.0]
              :target   [500.0 500.0   0.0]
              :up       [  0.0   1.0    0.0]
              :fov      45.0
              :near     1.0}
     :lines []
     :grids []
     :elements [
                ;; 地面透视辅助
                ;{:type :two-point-perspective
                ; :vp0                 [200.0 200.0]   ;; 左灭点（视平线上）
                ; :vp1                 [800.0 300.0]   ;; 右灭点（视平线上）
                ; :corner              [500.0 500.0]   ;; 墙角点（画面下方）
                ; :lines               12              ;; 每个灭点 12 条射线（均匀分布在 180° 内）
                ; :include-horizon     true                  ;; 包括地平面
                ; :show-opposite-half  false           ;; 只画角点方向半平面（标准两点透视）
                ; :average-distance   true
                ; :average-depth       true
                ; :distance           -60.0}
                ;
                ;;; 墙面透视辅助
                ;{:type :two-point-perspective
                ; :vp0                 [200.0 200.0]   ;; 左灭点（视平线上）
                ; :vp1                 [800.0 300.0]   ;; 右灭点（视平线上）
                ; :corner              [500.0 500.0]   ;; 墙角点（画面下方）
                ; :lines               24              ;; 每个灭点 12 条射线（均匀分布在 180° 内）
                ; :include-horizon     true                  ;; 包括地平面
                ; :show-opposite-half  false           ;; 只画角点方向半平面（标准两点透视）
                ; :average-distance    true
                ; :average-depth       false
                ; :distance           -60.0}
                ;
                ;{:type :two-point-perspective
                ; :vp0    [200.0 200.0]   ;; 左灭点
                ; :vp1    [800.0 300.0]   ;; 右灭点
                ; :corner [500.0 500.0]   ;; 墙角点
                ; :lines  24
                ; :include-horizon     true
                ; :show-opposite-half  false
                ; :average-depth-full  true
                ; :mp-distance         300.0   ;; 测量点到另一灭点的像素距离
                ; :depth-step-full      40.0}  ;; 垂直参考线刻度间距

                ;{:type :three-point-perspective
                ; :vp0    [200.0 300.0]   ;; 左灭点（视平线上）
                ; :vp1    [800.0 300.0]   ;; 右灭点（视平线上）
                ; :vp2    [500.0 800.0]   ;; 高度灭点（画面下方，形成仰视）
                ; :corner [500.0 400.0]   ;; 角点（三个面的交汇点，位于灭点三角形内部）
                ; :lines               18
                ; :show-opposite-half  false
                ; :include-horizon     true}

                ;; 三点透视等距垂直模式测试数据
                ;{:type :three-point-perspective
                ; :vp0    [200.0 300.0]   ;; 左灭点（视平线左侧）
                ; :vp1    [800.0 300.0]   ;; 右灭点（视平线右侧）
                ; :vp2    [500.0 800.0]   ;; 高度灭点（画面下方，形成仰视）
                ; :corner [500.0 450.0]   ;; 角点（三个面的交汇点，位于灭点三角形内部偏上）
                ; :lines               18
                ; :show-opposite-half  false
                ; :include-horizon     true
                ; :average-distance    true       ;; 启用等距垂直模式
                ; :distance           -60.0       ;; 负值使射线指向角点上方，模拟墙面竖向分割
                ; :reference-vp-index   2}        ;; 以高度灭点（vp2）作为垂直参考轴

                ;; 简化等深三点透视测试数据 (倒数分割法)
                ;{:type :three-point-perspective
                ; :vp0                [200.0 300.0]   ;; 左灭点
                ; :vp1                [800.0 300.0]   ;; 右灭点
                ; :vp2                [500.0 800.0]   ;; 高度灭点（仰视）
                ; :corner             [500.0 450.0]   ;; 角点
                ; :lines               12
                ; :show-opposite-half  false
                ; :include-horizon     true
                ; :simplified-depth    true          ;; 启用简化等深
                ; :base-depth          1.0
                ; :depth-step          1.0
                ; :reference-vp-index  2}           ;; 以高度灭点作为深度参考

                {:type :three-point-perspective
                 :vp0               [200.0 600.0]
                 :vp1               [800.0 500.0]
                 :vp2               [500.0 200.0]
                 :corner            [500.0 450.0]
                 :lines              12
                 :show-opposite-half false
                 :include-horizon    true
                 :measured-depth     true
                 :mp-distance        300.0
                 :depth-step         40.0}
                  ]}))

(defn create-canvas!
  "创建空白画布"
  ([w h] (create-canvas! (keyword (str (UUID/randomUUID))) w h))
  ([id w h]
   (let [test-persp (make-test-two-vanish-point-perspective-layer)
         cd (CanvasData. id w h [test-persp] nil)]   ;; 将测试图层放入 layers 向量
     (kcc/insert! :krro.painting/canvas (assoc cd :id id))
     cd)))


(defn delete-canvas!
  "删除画布，相关资源由 rdb 负责级联删除."
  [id]
  (kcc/delete-by-id! :krro.painting/canvas id))

(defn save-canvas-data!
  "创建或更新画布数据"
  [canvas-id cd]
  (kcc/update-by-id! :krro.painting/canvas canvas-id (constantly cd)))

(defn canvas-data
  "查询画布数据，当查询的是非代理数据或确认数据已经激活使用允许使用."
  ^CanvasData
  ([canvas-id]
   (kcc/select-by-id :krro.painting/canvas canvas-id))
  ([canvas-id db-map]                                              ;; 从指定map查询数据，用于双向绑定检查更新
   (get-in db-map [:krro.painting/canvas canvas-id])))

(defn canvas-data!
  "查询并激活画布数据."
  ^CanvasData [canvas-id]
  (proj/get-in-project! [:krro.painting/canvas canvas-id]))

(defn ensure-canvas-data!
  "确保画布存在并返回激活的 CanvasData。"
  [canvas-id width height]
  (or (canvas-data! canvas-id)
      (create-canvas! canvas-id width height)))

(defn canvas-size
  "查询画布尺寸，这两个是非代理资源，保持键一致就能多态获取了,无需激活."
  [canvas-id]
  (when-let [cd (canvas-data! canvas-id)]
    [(:width cd) (:height cd)]))

(defn deactivate-canvas!
  "将 CanvasData 编码回代理 map, 日后拓展虚拟代理时候可以把画布数据保存到外部."
  [canvas-id]
  (proj/deactivate-resource! [:krro.painting/canvas canvas-id]))

(defn current-layer-id
  ([canvas-id]
   (when-let [cd (canvas-data canvas-id)]
     (:current-layer-id cd)))
  ([canvas-id db-map]
   (when-let [cd (canvas-data canvas-id db-map)]
     (:current-layer-id cd))))

;; 持久化多方法.
(defmulti persistable-layer :type)
(defmulti persistable-layer! (fn [layer _canvas-id] (:type layer)))
(defmethod persistable-layer! :default
  [layer _canvas-id] (persistable-layer layer))
(defmulti active-layer! (fn [layer _canvas-id] (:type layer)))

(defmethod persistable-layer :default [layer] layer)

(defmethod active-layer! :default [layer _canvas-id] layer)


(def canvas-codec-plugin-def
  {:type     :krro.plugin/resource-codec
   :id       :krro.painting/canvas-codec
   :resource :krro.painting/canvas-data
   :pred     #(instance? CanvasData % )
   :encoder  (fn [c _ctx]
               (let [id (:id c)
                     encoded-layers (mapv #(persistable-layer! % id) (:layers c))]
                 {:krro/type :krro.painting/canvas-data
                  :id id
                  :width  (:width c)
                  :height (:height c)
                  :layers encoded-layers
                  :current-layer-id (:current-layer-id c)}))
   ;; 解码时候自己负责恢复句柄
   :decoder  (fn [m]
               (let [id (:id m)
                     w (:width m)
                     h (:height m)
                     decoded-layers (mapv #(active-layer! % id) (:layers m))]
                 (map->CanvasData {:id id :width w :height h :layers decoded-layers
                                   :current-layer-id (:current-layer-id m)})))})



;; 其他低级查询
(defn layers-by-id
  ([canvas-id] (:layers (canvas-data canvas-id))))

(defn layers-by-id!
  ([canvas-id] (:layers (canvas-data! canvas-id))))

(defn find-layer-in-canvas! [canvas-id layer-id]
  (when-let [ls (layers-by-id! canvas-id)]
    (lc/find-layer layer-id ls)))

(defn visible-layer?
  ([canvas-id layer-id]
   (when-let [cd (canvas-data canvas-id)]
     (when-let [l (lc/find-layer layer-id (:layers cd))]
       (:visible? l))))
  ([canvas-id layer-id db-map]
   (when-let [cd (canvas-data canvas-id db-map)]
     (when-let [l (lc/find-layer layer-id (:layers cd))]
       (:visible? l)))))

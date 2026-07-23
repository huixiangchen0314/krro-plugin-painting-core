(ns top.kzre.krro.plugin.painting.core.tool.fill
  "填充工具：单击图层即可填充连通区域（油漆桶）。
   在 apply! 中仅记录点击事件，实际填充逻辑在 commit! 中完成，
   以确保撤销/重做正确处理。"
  (:require
    [top.kzre.krro.plugin.painting.core.brush.core :as brush]
    [top.kzre.krro.plugin.painting.core.ops.layer :as layer]
    [top.kzre.krro.plugin.painting.core.ops.undo :as undo]
    [top.kzre.krro.plugin.painting.core.tool.protocol :as tp]
    [top.kzre.krro.plugin.painting.core.tool.util :as tool-util])
  (:import
    (top.kzre.colorutils.color RGB)
    (top.kzre.krro.canvas.raster.bloodfill
      ColorMatchers
      FloodFillExecutor
      FloodFillRequest
      LineArtDetectors)
    (top.kzre.krro.util.tile TiledCanvas)))

;; ── 笔刷配置辅助 ──────────────────────────────────
(defn- get-brush []
  (or @brush/global-brush brush/default-brush))

;; ── 填充参数提取 ──────────────────────────────────
(defn- build-fill-request
  "根据当前笔刷规格和点击位置，构造 FloodFillRequest。"
  [canvas brush-spec local-x local-y _layer ctx]
  (let [width  (get-in ctx [:data :width] 0)   ;; 画布总宽
        height (get-in ctx [:data :height] 0)   ;; 画布总高
        ;; 从笔刷配置读取参数，提供默认值
        fill-color (get brush-spec :color (RGB/rgba 0.0 0.0 0.0 1.0))
        tolerance  (float (get brush-spec :fill-tolerance 0.0))
        contiguous (get brush-spec :fill-contiguous true)
        anti-alias (get brush-spec :fill-anti-alias true)
        blend-mode (get brush-spec :fill-blend-mode nil) ; nil 表示替换
        protect-opacity (get brush-spec :fill-protect-opacity false)
        expand-radius (float (get brush-spec :fill-expand-radius 0.0))
        protect-line-art (get brush-spec :fill-protect-line-art false)
        ;; 颜色匹配器默认使用平方欧氏距离
        matcher (ColorMatchers/euclideanSquare)
        ;; 线稿检测器默认使用亮度阈值
        line-detector (LineArtDetectors/defaultDetector)
        ;; 暂时忽略选区，预留 mask 为 nil
        mask nil]
    (-> (FloodFillRequest/newBuilder)
        (.targetCanvas canvas)
        (.seed (double local-x) (double local-y))
        (.fillColor (float-array fill-color))
        (.tolerance tolerance)
        (.contiguous contiguous)
        (.antiAlias anti-alias)
        (.blendMode blend-mode)
        (.protectOpacity protect-opacity)
        (.expandRadius expand-radius)
        (.protectLineArt protect-line-art)
        (.colorMatcher matcher)
        (.lineArtDetector line-detector)
        (.mask mask)
        (.size width height)
        (.build))))

;; ═══════════════════════════════════════════════════════
;; 工具实现
;; ═══════════════════════════════════════════════════════
(defrecord FillTool [parent-inv   ;; atom: 缓存父逆矩阵
                     pending-fill ;; atom: 存储待处理的填充请求数据
                     ]
  tp/ITool
  (id [_] :fill)
  (overlay [_] nil)   ;; 填充工具无需光标预览

  (begin! [_ layer rt _ctx]
    (reset! parent-inv nil)
    (reset! pending-fill nil)
    {:layer layer :state rt})

  (end! [_ layer rt _ctx]
    (reset! parent-inv nil)
    (reset! pending-fill nil)
    {:layer layer :state rt})

  (apply! [_ layer _rt ev ctx]
    ;; 只响应 press 事件，其他忽略
    (if (= :press (:type ev))
      (let [{:keys [event parent-inv-new]}
            (tool-util/transform-event ev layer (:data ctx) :parent-inv @parent-inv)]
        (reset! parent-inv parent-inv-new)
        (let [local-x (:x event)
              local-y (:y event)
              brush-spec (get-brush)
              ;; 将填充所需的数据暂存，等待 commit! 时使用
              fill-data {:layer layer
                         :local-x local-x
                         :local-y local-y
                         :brush-spec brush-spec}]
          (reset! pending-fill fill-data)
          :no-replace))
      :idle))

  (preview! [_ layer rt _ctx]
    ;; 无预览
    {:layer layer :state rt})

  (commit! [_ layer rt ctx]
    (if-let [{:keys [local-x local-y brush-spec]} @pending-fill]
      (let [canvas (:canvas layer)
            backup-canvas (:canvas (:layer-backup rt))
            ;; 保存填充前的画布快照用于撤销
            ^TiledCanvas tmp-canvas
            (doto (TiledCanvas. (.getTileSize backup-canvas)
                                (.getDefaultPixel backup-canvas))
              (.shareFrom backup-canvas))
            ;; 构建请求
            request (build-fill-request canvas brush-spec local-x local-y layer ctx)
            ;; 执行填充
            result (FloodFillExecutor/fill request)
            new-canvas (.getCanvas result)
            dirties (.getDirtyTiles result)]
        ;; 如果无变化，直接返回原图层
        (if (and (.isChanged result) (seq dirties))
          (let [new-layer (assoc layer :canvas new-canvas)]
            (layer/replace-layer! (:canvas-id ctx) new-layer)
            ;; 记录撤销（使用 tmp-canvas 作为填充前的状态）
            (undo/record-raster-stroke! (:canvas-id ctx) (:id layer)
                                        tmp-canvas new-canvas dirties)
            ;; 清理暂存
            (reset! pending-fill nil)
            (.clear tmp-canvas)
            {:layer new-layer
             :state (assoc rt
                      :layer-backup {:type :raster :canvas new-canvas}
                      :dirty-tiles dirties)})
          ;; 无变化，返回原状
          (do
            (.clear tmp-canvas)
            (reset! pending-fill nil)
            {:layer layer :state rt})))
      ;; 没有暂存数据（不应该发生）
      {:layer layer :state rt})))

(defn make-fill []
  (->FillTool (atom nil) (atom nil)))
(ns top.kzre.krro.plugin.painting.editor.core.ui.layer-browser
  "图层面板：标题、图层列表（vnode 实现，支持拖拽排序）和工具栏。
   所有样式值均为字符串，符合 spec.element 规范。"
  (:require
    [top.kzre.krro.canvas.core.layer.core :as layer-core]
    [top.kzre.krro.plugin.painting.core.ops.layer :as layer]
    [top.kzre.krro.plugin.painting.core.ops.layer-undo :as layer-undo]
    [top.kzre.krro.plugin.painting.core.state :as state]
    [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
    [top.kzre.krro.plugin.painting.core.project.layer-meta :as pm]
    [top.kzre.krro.ui.core.spec.drag :as drag-spec]))

;; ── 内部工具：递归展平图层 ──────────────────────────
(defn- flatten-layers
  ([layers] (flatten-layers layers [] 0))
  ([layers parent-path indent]
   (mapcat (fn [idx layer]
             (let [current-path (conj parent-path idx)]
               (cons {:layer layer :path current-path :indent indent}
                     (when (= :group (:type layer))
                       (flatten-layers (:layers layer) current-path (inc indent))))))
           (range)
           layers)))

;; ── 图层行 vnode ────────────────────────────────────
(defn- layer-row-vnode [{:keys [layer path indent]} selected-id canvas-id]
  (let [lid       (:id layer)
        name      (or (:name layer) (str lid))
        visible?  (get layer :visible? true)
        locked?   (:locked? (pm/layer-meta lid))
        is-group? (= :group (:type layer))
        indent-str (apply str (repeat indent "  "))]
    [:block {:key lid                                    ;; 稳定 key 用于 diff 复用
             :class "layer-row"
             :style {:padding-left (str (* indent 12) "px")}  ;; 字符串值
             ;; 拖拽源：传递图层 ID 字符串
             :drag-source (drag-spec/drag-source
                            (fn [_] (str lid))
                            :modes [:move])
             ;; 拖拽目标：接受移动并处理放置
             :drag-target (drag-spec/drag-target
                            (fn [node e] true)            ;; 总是接受
                            (fn [node e]
                              ;; 动态获取路径，不依赖节点用户数据
                              (let [source-id (keyword (:data e))
                                    target-id lid
                                    layers (pc/layers-by-id! canvas-id)
                                    source-path (layer-core/find-layer-path source-id layers)
                                    target-path (layer-core/find-layer-path target-id layers)]
                                (when (and source-path target-path (not= source-id target-id))
                                  (layer-undo/move-layer-undo! canvas-id source-path target-path)))))
             :direction :horizontal}
     ;; 可见性复选框（仅非组图层显示）
     (when-not is-group?
       [:check-box {:checked? visible?
                    :getter   (fn [db-map] (pc/visible-layer? canvas-id lid db-map))
                    :setter   (fn [v] (layer-undo/set-layer-visibility! canvas-id lid v))}])
     ;; 图层名称（点击选中）
     [:text {:content  (str indent-str name)
             :style    (if (= lid selected-id)
                         {:fill "yellow" :font-weight "bold"}   ;; 字符串值
                         {:fill "lightgray"})
             :on-click (fn [_] (layer/set-selected-layer-id! canvas-id lid))}]
     ;; 锁定图标
     (when locked?
       [:text {:content "🔒" :style {:font-size "10" :fill "gray"}}])  ;; 字符串值
     ;; 组展开图标（占位）
     (when is-group?
       [:text {:content "▸" :style {:font-size "10" :fill "lightgray"}}])]))  ;; 字符串值

;; ── 图层面板（容器 + 列表 + 工具栏） ────────────────
(defn layer-panel-vnode [canvas-id f]
  (let [layers      (pc/layers-by-id! canvas-id)
        selected-id (state/selected-layer-id canvas-id)
        flat        (flatten-layers layers)]
    [:block {:class "layer-browser" :direction :vertical}
     [:text {:class "layer-browser-title" :content "Layers"}]
     ;; 图层列表：将 mapv 结果展开为多个子节点
     (into [:block {:class "layer-list" :direction :vertical}]
           (mapv (fn [info] (layer-row-vnode info selected-id canvas-id)) flat))
     ;; 工具栏
     [:block {:class "layer-toolbar" :direction :horizontal}
      [:button {:class "layer-toolbar-button" :content "＋"
                :on-click (fn [_] (layer-undo/add-vector-layer-over-selected-undo! canvas-id))}]
      [:button {:class "layer-toolbar-button" :content "×"
                :on-click (fn [_]
                            (when-let [path (layer/selected-layer-path canvas-id)]
                              (layer-undo/remove-layer-at-undo! canvas-id path)))}]
      [:button {:class "layer-toolbar-button" :content "▣"
                :on-click (fn [_]
                            (when-let [sid (state/selected-layer-id canvas-id)]
                              (layer-undo/duplicate-layer-undo! canvas-id sid)))}]]]))
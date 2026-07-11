(ns top.kzre.krro.plugin.painting.ui.layer-list
  "图层列表组件，支持拖拽排序、图层组层级显示。"
  (:require
    [top.kzre.krro.core.hook :as hook]
    [top.kzre.krro.plugin.painting.canvas.project :as proj]
    [top.kzre.krro.plugin.painting.canvas.layer-undo :as layer]
    [top.kzre.krro.plugin.painting.canvas.state :as state]
    [top.kzre.krro.plugin.painting.spec :as spec]
    [top.kzre.krro.ui.javafx.core :refer [make-component]])
  (:import
    (javafx.application Platform)
    (javafx.event EventHandler)
    (javafx.geometry Pos)
    (javafx.scene.control CheckBox Label)
    (javafx.scene.input ClipboardContent TransferMode)
    (javafx.scene.layout HBox VBox)))

;; ── 内部工具：递归展平图层 ──────────────────────────
(defn- flatten-layers
  "递归遍历图层列表，返回 [{:layer map, :path vec, :indent int}]。"
  ([layers]
   (flatten-layers layers [] 0))
  ([layers parent-path indent]
   (mapcat (fn [idx layer]
             (let [current-path (conj parent-path idx)]
               (cons {:layer layer :path current-path :indent indent}
                     (when (= :group (:type layer))
                       (flatten-layers (:layers layer) current-path (inc indent))))))
           (range)
           layers)))

;; ── 创建图层行 ───────────────────────────────────────
(defn- create-layer-row [{:keys [layer path indent]} selected-id canvas-id]
  (let [lid       (:id layer)
        name      (or (:name layer) (str lid))
        visible?  (get layer :visible? true)
        locked?   (:locked? (proj/get-layer-meta lid))            ;; 从 project 获取元数据
        is-group? (= :group (:type layer))
        ;; 缩进空格
        indent-str (apply str (repeat indent "  "))
        row       (doto (HBox. 5.0) (.setAlignment Pos/CENTER_LEFT))
        vis-cb    (doto (CheckBox.)
                    (.setSelected visible?)
                    (.setOnAction
                      (reify EventHandler
                        (handle [_ _]
                          (layer/update-layer-by-id-undo! canvas-id lid
                                                          #(assoc % :visible? (not visible?)))))))
        name-lbl  (doto (Label. (str indent-str name))
                    (.setStyle (if (= lid selected-id)
                                 "-fx-text-fill: yellow; -fx-font-weight: bold;"
                                 "-fx-text-fill: lightgray;"))
                    (.setOnMouseClicked
                      (reify EventHandler
                        (handle [_ _]
                          (state/set-selected-layer-id! canvas-id lid)))))
        lock-icon (when locked? (doto (Label. "🔒")
                                  (.setStyle "-fx-font-size:10; -fx-text-fill: gray;")))
        ;; 组展开/折叠图标（占位）
        expand-icon (when is-group?
                      (doto (Label. "▸")
                        (.setStyle "-fx-font-size:10; -fx-text-fill: lightgray;")))]
    ;; 组装行
    (doto (.getChildren row)
      (.add (if is-group? expand-icon vis-cb))
      (.add name-lbl))
    (when lock-icon (doto (.getChildren row)(.add lock-icon)))
    ;; 设置拖拽
    (.setOnDragDetected row
                        (reify EventHandler
                          (handle [_ e]
                            (let [db (.startDragAndDrop row (into-array TransferMode [TransferMode/MOVE]))
                                  content (ClipboardContent.)]
                              (.putString content (str lid))
                              (.setContent db content)
                              (.consume e)))))
    (.setOnDragOver row
                    (reify EventHandler
                      (handle [_ e]
                        (.acceptTransferModes e (into-array TransferMode [TransferMode/MOVE]))
                        (.consume e))))
    (.setOnDragDropped row
                       (reify EventHandler
                         (handle [_ e]
                           (let [source    (.getGestureSource e)
                                 source-id (when source (-> source .getUserData :layer-id))
                                 target-id lid]
                             (when (and source-id target-id (not= source-id target-id))
                               ;; 获取源路径和目标路径（从用户数据中获取）
                               (let [source-path (when source (-> source .getUserData :path))
                                     target-path (-> row .getUserData :path)]
                                 (when (and source-path target-path)
                                   (layer/move-layer-undo! canvas-id source-path target-path))))
                             (.setDropCompleted e true)
                             (.consume e)))))
    ;; 保存路径和图层 ID 到用户数据
    (.setUserData row {:layer-id lid :path path})
    row))

;; ── 构建整个列表 ──────────────────────────────────────
(defn- build-layer-list [root canvas-id]
  (let [layers      (proj/layers-by-id! canvas-id)
        selected-id (state/selected-layer-id canvas-id)
        flat        (flatten-layers layers)
        rows        (mapv #(create-layer-row % selected-id canvas-id) flat)]
    (.clear (.getChildren root))
    (doseq [row rows]
      (.add (.getChildren root) row))))

;; ── 组件工厂 ──────────────────────────────────────────
(def create-layer-list
  (make-component [:krro.painting/canvas-id]
                  (fn [] (doto (VBox.) (.setSpacing 5.0)))
                  (fn [^VBox root _old-props props f]
                    (let [canvas-id (:krro.painting/canvas-id props)
                          refresh   (fn [] (Platform/runLater
                                             (fn [] (build-layer-list root canvas-id))))
                          cb1 (fn [cid] (when (= cid canvas-id)
                                          (refresh)))
                          cb2 (fn [cid lid] (when (= cid canvas-id) (refresh)))]
                      (hook/add-hook! spec/layer-changed-hook-key cb1)
                      (hook/add-hook! spec/selected-layer-changed-hook-key cb2)
                      (build-layer-list root canvas-id)
                      (fn []
                        (hook/remove-hook! spec/layer-changed-hook-key cb1)
                        (hook/remove-hook! spec/selected-layer-changed-hook-key cb2)
                        (.clear (.getChildren root)))))))
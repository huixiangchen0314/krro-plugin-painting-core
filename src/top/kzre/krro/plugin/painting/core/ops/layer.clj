(ns top.kzre.krro.plugin.painting.core.ops.layer
  "图层操作：纯函数、副作用函数与带撤销函数。
   基于路径管理嵌套图层组，自动同步到项目原子。"
  (:require
   [top.kzre.krro.canvas.core.layer.core :as lc]
   [top.kzre.krro.core.core :as kcc]
   [top.kzre.krro.core.frame :as frame]
   [top.kzre.krro.core.hook :as hook]
   [top.kzre.krro.plugin.painting.core.ops.backup :as backup]
   [top.kzre.krro.plugin.painting.core.project.canvas :as pc]
   [top.kzre.krro.plugin.painting.core.spec :as spec]
   [top.kzre.krro.plugin.painting.core.state :as state]
   [top.kzre.krro.plugin.painting.core.viewport :as vp])
  (:import
   (javafx.application Platform)
   (top.kzre.krro.plugin.painting.core.project.canvas CanvasData)))

;; ── 工具函数 ──────────────────────────────────────
(defn- with-layers [^CanvasData old-cd new-layers]
  (assoc old-cd :layers new-layers))

(defn update-project! [canvas-id new-cd]
  (kcc/update-by-id! :krro.painting/canvas canvas-id (constantly new-cd)))


(defn refresh-canvas-frames! [canvas-id]
  (when-let [rt (state/canvas-runtime canvas-id)]
    (let [preview (state/preview-buffer rt)
          [w h]   (pc/canvas-size canvas-id)]
      (state/render-canvas! canvas-id preview)
      (doseq [f (state/frames-with-canvas-id canvas-id)]
        (when-let [ufn (frame/param f spec/update-fn-key)]
          (let [viewport (vp/get-viewport f)]
            (Platform/runLater #(ufn preview w h viewport))))))))

(defn refresh-canvas-and-layer!
  "刷新画布并重新渲染UI布局, 当图层发生了影响画布渲染的行动时候使用."
  [canvas-id]
  (refresh-canvas-frames! canvas-id)
  (hook/run-hook! spec/layer-changed-hook-key canvas-id))

(defn set-selected-layer-id! [canvas-id layer-id]
  (let [cd (pc/canvas-data! canvas-id)
        old-id (:selected-layer-id cd)]
    (when (not= old-id layer-id)
      (let [layers (:layers cd)
            old-layer (when old-id (lc/find-layer old-id layers))
            new-layer (when layer-id (lc/find-layer layer-id layers))
            runtime (state/canvas-runtime canvas-id)]
        ;; 更新项目数据
        (kcc/update-by-id! :krro.painting/canvas canvas-id #(assoc % :selected-layer-id layer-id))
        ;; 备份状态更新
        (when old-layer (backup/release-backup! old-layer runtime))
        (when-let [new-st (backup/backup-layer! new-layer runtime)]
          (swap! state/canvas-runtimes assoc canvas-id new-st))
        ;; 触发钩子
        (hook/run-hook! spec/selected-layer-changed-hook-key canvas-id layer-id)))))

;; ── 路径查询 ──────────────────────────────────────
(defn selected-layer-path [canvas-id]
  (when-let [selected-id (state/selected-layer-id canvas-id)]
    (lc/find-layer-path selected-id (pc/layers-by-id! canvas-id))))

(defn add-layer-at
  "纯：在路径处插入图层。
   返回 {:canvas-data, :layer, :layer-id, :path}"
  [cd path layer]
  (let [new-layers (lc/insert-layer path layer (:layers cd))
        new-cd     (with-layers cd new-layers)]
    {:canvas-data new-cd
     :layer       layer
     :layer-id    (:id layer)
     :path        path}))

;; ── 删除图层 ──────────────────────────────────────

(defn remove-layer-at
  "纯：删除路径处的图层。
   返回 {:canvas-data, :removed, :new-selected-id}"
  [cd selected-id path]
  (let [layers        (:layers cd)
        [final-layers removed] (lc/remove-layer path layers)
        new-cd        (with-layers cd final-layers)
        layer-id      (:id removed)
        ;; 计算新选择的图层
        idx           (last path)
        new-sel       (if (= selected-id layer-id)
                        (let [new-parent (lc/parent-container path final-layers)
                              new-idx    (min idx (dec (count new-parent)))]
                          (when (>= new-idx 0) (:id (nth new-parent new-idx))))
                        selected-id)]
    {:canvas-data new-cd
     :removed     removed
     :layer-id    layer-id
     :new-selected-id new-sel}))


;; ── 移动图层 ──────────────────────────────────────

(defn move-layer
  "纯：将图层从 old-path 移到 new-path，返回新的 CanvasData。"
  [cd old-path new-path]
  (let [layers       (:layers cd)
        [temp-layers moved] (lc/remove-layer old-path layers)
        same-parent? (= (butlast old-path) (butlast new-path))
        old-idx (last old-path)
        new-idx (last new-path)
        adjusted-new-path (if (and same-parent? (< old-idx new-idx))
                            (conj (butlast new-path) (dec new-idx))
                            new-path)
        final-layers (lc/insert-layer adjusted-new-path moved temp-layers)]
    (with-layers cd final-layers)))

(defn move-layer! [canvas-id old-path new-path]
  (when-let [new-cd (move-layer (pc/canvas-data! canvas-id) old-path new-path)]
    (state/invalidate-canvas-dirty! canvas-id)
    (update-project! canvas-id new-cd)
    (refresh-canvas-frames! canvas-id)
    (hook/run-hook! spec/layer-changed-hook-key canvas-id)
    new-cd))


;; ── 更新图层 ──────────────────────────────────────

(defn update-layer-at [cd path updater]
  (let [layers      (:layers cd)
        idx         (last path)
        parent-path (butlast path)
        parent      (lc/parent-container path layers)
        old-layer   (nth parent idx)
        new-layer   (updater old-layer)
        new-parent  (assoc-in parent [idx] new-layer)]
    (if (seq parent-path)
      (let [root-updated (assoc-in layers (interleave parent-path (repeat :layers)) new-parent)]
        (with-layers cd root-updated))
      (with-layers cd (assoc layers idx new-layer)))))

(defn update-layer-by-id [cd layer-id updater]
  (when-let [path (lc/find-layer-path layer-id (:layers cd))]
    (update-layer-at cd path updater)))

;; 默认更新行为，触发渲染，但不维护侧表数据.
;; 对应不会触发渲染，或者需要更新侧表数据的操作，应当独立实现.
(defn update-layer-at! [canvas-id path updater]
  (when-let [new-cd (update-layer-at (pc/canvas-data! canvas-id) path updater)]
    (update-project! canvas-id new-cd)
    (state/invalidate-canvas-dirty! canvas-id)
    (refresh-canvas-frames! canvas-id)
    (hook/run-hook! spec/layer-changed-hook-key canvas-id)
    new-cd))

(defn update-layer-by-id! [canvas-id layer-id updater]
  (when-let [new-cd (update-layer-by-id (pc/canvas-data! canvas-id) layer-id updater)]
    (update-project! canvas-id new-cd)
    (state/invalidate-canvas-dirty! canvas-id)
    (refresh-canvas-frames! canvas-id)
    (hook/run-hook! spec/layer-changed-hook-key canvas-id)
    new-cd))

;; 这里的 非回退 replace-layer! 不允许发生引用更新
(defn replace-layer! [canvas-id layer]
  (let [layer-id (:id layer)
        cd (pc/canvas-data! canvas-id)
        layers (:layers cd)
        path (lc/find-layer-path layer-id layers)]
    (when path
      ;; 总是更新，因为我们允许图层中有对象存在，内部状态无法简单比较
      (update-layer-at! canvas-id path (fn [_] layer)))))

(defn auto-select-layer! [canvas-id]
  (let [current-id (state/selected-layer-id canvas-id)]
    (if (nil? current-id)
      (let [layers (pc/layers-by-id! canvas-id)]
        (when-let [top (last layers)]
          (let [id (:id top)]
            (set-selected-layer-id! canvas-id id)
            id)))
      current-id)))


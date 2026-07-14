(ns top.kzre.krro.plugin.painting.ui.tool-bar
  (:require
    [top.kzre.krro.plugin.painting.canvas.state :as state]
    [top.kzre.krro.plugin.painting.project.canvas :as pc]
    [top.kzre.krro.plugin.painting.tool.protocol :as tp]
    [top.kzre.krro.plugin.painting.tool.registry :as registry])
  (:import (top.kzre.krro.plugin.painting.tool.brush BrushTool)
           (top.kzre.krro.plugin.painting.tool.move MoveTool)))

(defn- tool-id [tool-inst]
  (cond
    (instance? BrushTool tool-inst) :brush
    (instance? MoveTool tool-inst) :move
    :else nil))

(defn tool-bar-vnode [canvas-id frame]
  (let [current-tool (state/current-tool canvas-id)
        active-id    (tool-id current-tool)
        ;; 准备一次上下文供工具切换使用
        layer        (state/selected-layer! canvas-id)
        data         (pc/canvas-data! canvas-id)
        runtime      (state/canvas-runtime canvas-id)
        ctx          (when (and layer data runtime)
                       (tp/make-context canvas-id frame data runtime))]
    (into [:tool-bar {:class "tool-bar"}]
          (for [{:keys [id name icon make-fn]} registry/tools]
            [:button {:key id
                      :class (str "tool-btn" (when (= id active-id) " active"))
                      :content (str icon " " name)
                      :on-click (fn [_]
                                  (when-let [old (state/current-tool canvas-id)]
                                    (tp/end! old layer ctx))
                                  (let [new-tool (make-fn)]
                                    (tp/begin! new-tool layer ctx)
                                    (state/set-current-tool! canvas-id new-tool)))}]))))
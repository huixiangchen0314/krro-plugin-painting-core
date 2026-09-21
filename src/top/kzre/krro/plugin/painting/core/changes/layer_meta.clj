(ns top.kzre.krro.plugin.painting.core.changes.layer-meta)

(defrecord LayerVisibilityChanged [layer-id visible?])

(defrecord LayerLockChanged [layer-id lock?])

(defrecord LayerAlphaLockChanged [layer-id alpha-lock?])

(defrecord LayerGroupExpandChanged [layer-id expand?])

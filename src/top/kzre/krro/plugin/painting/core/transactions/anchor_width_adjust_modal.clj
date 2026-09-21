(ns top.kzre.krro.plugin.painting.core.transactions.anchor-width-adjust-modal)


(defrecord AnchorWidthAdjustTransaction
  [selected active-anchor layer-backup
   init-distance last-distance])
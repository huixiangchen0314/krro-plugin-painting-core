(ns top.kzre.krro.plugin.painting.core.transactions.anchor-adjust-width)


(defrecord AnchorAdjustWidthTransaction [selected active-anchor layer-backup
                                         init-distance last-distance])
(ns top.kzre.krro.plugin.painting.core.transactions.anchor-extrude)


(defrecord AnchorExtrudeTransaction [active-anchor layer-backup anchor-backup
                                     new-anchor second-anchor])

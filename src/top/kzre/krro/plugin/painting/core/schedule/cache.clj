(ns top.kzre.krro.plugin.painting.core.schedule.cache
  (:import (java.lang AutoCloseable)
           (top.kzre.krro.util.tile TiledCanvas)))

;; 光栅数据缓存
(defrecord RasterCache
  [^TiledCanvas canvas                                      ;; 缓存时候的光栅数据
   ^floats view-matrix                                      ;; 缓存时候的视口仿射变换矩阵
   ]
  AutoCloseable
  (close [_]
    (when (and canvas (not (.isClosed canvas)))
      (.close canvas))))

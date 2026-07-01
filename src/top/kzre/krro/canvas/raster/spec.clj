(ns top.kzre.krro.canvas.raster.spec
  "光栅图层特有属性规格 —— 图层持有一个完整的画布。"
  (:require [clojure.spec.alpha :as s]
            [top.kzre.krro.canvas.core.layer.spec :as layer-spec]
            [top.kzre.krro.canvas.core.canvas.spec :as canvas-spec]))

;; 光栅图层只需包含一个有效的画布
(s/def ::raster-props
  (s/keys :req-un [::canvas-spec/canvas]))

(defmethod layer-spec/layer-spec :raster [_]
  ::raster-props)
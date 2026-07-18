(ns top.kzre.krro.canvas.raster.spec
  "光栅图层特有属性规格 —— 图层持有一个完整的瓦片画布，并可选记录脏瓦片。"
  (:require
    [clojure.spec.alpha :as s]
    [top.kzre.krro.util.tiled-canvas :as tc]
    [top.kzre.krro.canvas.core.layer.spec :as layer-spec]))

;; 复用 tiled-canvas 的 canvas 规格
(s/def ::canvas ::tc/canvas)

;; 瓦片键：位编码后的 long，兼容任意整数值
(s/def ::tile-key integer?)


(s/def ::origin-x int?)
(s/def ::origin-y int?)

;; 光栅图层属性：画布必选，脏瓦片和原点可选
(s/def ::raster-props
  (s/keys :req-un [::canvas]
          :opt-un [::origin-x ::origin-y]))

(defmethod layer-spec/layer-spec :raster [_]
  ::raster-props)
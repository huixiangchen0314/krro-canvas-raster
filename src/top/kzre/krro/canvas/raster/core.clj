(ns top.kzre.krro.canvas.raster.core
  "光栅图层实现：通过画布 API 获取底层数组进行批量渲染，提升性能。
   提供激活光栅后端的便捷函数，替换 *merge-layer!* 并注册 :raster 图层。"
  (:require
    [top.kzre.krro.canvas.core.core :as c]
    [top.kzre.krro.canvas.core.layer.util :as lu]
    [top.kzre.krro.canvas.raster.spec]
    [top.kzre.krro.canvas.raster.util :as util])
  (:import
    (java.util Collection HashSet UUID)
    (top.kzre.krro.canvas.raster TiledCanvasRenderer)
    (top.kzre.krro.util.tile TiledCanvas)))

(defn make-raster-layer
  "创建一个光栅图层，包含一个 TiledCanvas 画布。
   可选关键字：
     :id          - 图层唯一标识（关键字，默认自动生成）
     :name        - 图层名称（默认 \"Raster Layer\"）
     :opacity     - 不透明度 0.0~1.0（默认 1.0）
     :blend-mode  - 混合模式关键字（默认 :normal）
     :visible?    - 是否可见（默认 true）
     :backend     - 渲染后端（默认 :default）
     :tile-size   - 瓦片大小（像素，默认 256）
     其他变换属性 :x, :y, :scale-x, :scale-y, :rotation 等"
  [& {:keys [id name opacity blend-mode visible? backend tile-size]
      :or   {id         (keyword (str "layer-" (UUID/randomUUID)))
             name       "Raster Layer"
             opacity    1.0
             tile-size  64
             blend-mode :normal
             visible?   true
             backend    :default}
      :as   opts}]
  (let [canvas (TiledCanvas. tile-size (float-array [0.0 0.0 0.0 0.0]))]
    (merge {:id         id
            :type       :raster
            :name       name
            :opacity    opacity
            :blend-mode blend-mode
            :visible?   visible?
            :backend    backend
            :canvas     canvas}
           (select-keys opts [:x :y :scale-x :scale-y :rotation :mask]))))


(defmethod c/render-layer! :raster
  [layer ^floats data w h {:keys [dirty-tiles] :as opts}]
  (let [canvas      (:canvas layer)                 ; TiledCanvas 实例
        blend-mode  (util/blend-mode-str (:blend-mode layer) :normal)
        opacity     (float (get layer :opacity 1.0))
        transform   (:transform layer lu/identity-matrix)
        matrix      (float-array transform)
        java-dirty  (when (seq dirty-tiles)
                      (HashSet. ^Collection dirty-tiles))]
    (TiledCanvasRenderer/blendTransformedTiled data (int w) (int h)
                                               canvas
                                               matrix blend-mode opacity
                                               java-dirty)))
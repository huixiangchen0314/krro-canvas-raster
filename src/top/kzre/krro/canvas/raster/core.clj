(ns top.kzre.krro.canvas.raster.core
  "光栅图层实现：通过画布 API 获取底层数组进行批量渲染，提升性能。
   提供激活光栅后端的便捷函数，替换 *merge-layer!* 并注册 :raster 图层。"
  (:require
    [top.kzre.krro.util.tiled-canvas :as tc]
    [top.kzre.krro.canvas.core.core :as c]
    [top.kzre.krro.canvas.core.layer.util :as lu]
    [top.kzre.krro.canvas.raster.spec]
    [top.kzre.krro.canvas.raster.util :as util])
  (:import
    (java.util UUID)
    (top.kzre.krro.canvas.raster Renderer)))

(defn make-raster-layer
  "创建一个光栅图层，包含一个 RasterCanvas。
   必选参数：width, height（整数）
   可选关键字：
     :id          - 图层唯一标识（关键字，默认自动生成）
     :name        - 图层名称（默认 \"Raster Layer\"）
     :opacity     - 不透明度 0.0~1.0（默认 1.0）
     :blend-mode  - 混合模式关键字（默认 :normal）
     :visible?    - 是否可见（默认 true）
     :backend     - 渲染后端（默认 :raster）
     其他变换属性 :x, :y, :scale-x, :scale-y, :rotation 等"
  [& {:keys [id name opacity blend-mode visible? backend tile-size]
                   :or   {id         (keyword (str "layer-" (UUID/randomUUID)))
                          name       "Raster Layer"
                          opacity    1.0
                          tile-size 256
                          blend-mode :normal
                          visible?   true
                          backend    :default}
                   :as   opts}]
  (let [canvas (tc/make-canvas :tile-size tile-size)]
    (merge {:id         id
            :type       :raster
            :name       name
            :opacity    opacity
            :blend-mode blend-mode
            :visible?   visible?
            :backend    backend
            :canvas     canvas}
           (select-keys opts [:x :y :scale-x :scale-y :rotation :mask]))))

(defn- merge-layer-impl
  [^floats data w h source]
  (let [src-data   (:data source)
        blend-mode (util/blend-mode-str (:blend-mode source) :normal)
        opacity    (float (get source :opacity 1.0))
        transform  (get source :transform lu/identity-matrix)]
    (Renderer/blendTransformed data src-data w h transform blend-mode opacity)))


(defn use-raster-merge-layer!
  []
  (alter-var-root #'c/*merge-layer!* (constantly merge-layer-impl)))


(defmethod c/render-layer! :raster
  [layer ^floats data w h]
  (let [canvas      (:canvas layer)
        {:keys [tiles tile-size]} canvas          ;; tiles 是 Clojure map (Long -> float[])
        blend-mode  (util/blend-mode-str (:blend-mode layer) :normal)
        opacity     (float (get layer :opacity 1.0))
        transform   (or (get layer :transform) lu/identity-matrix)
        matrix      (if (vector? transform) (float-array transform) (float-array transform))
        dirty-tiles (:dirty-tiles layer)]          ;; nil 或 Set<Long>
    (Renderer/blendTransformedTiled data (int w) (int h)
                                    tiles (int tile-size)
                                    matrix blend-mode opacity
                                    dirty-tiles)))
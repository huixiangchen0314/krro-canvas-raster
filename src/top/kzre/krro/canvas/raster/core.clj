(ns top.kzre.krro.canvas.raster.core
  "光栅图层实现：通过画布 API 获取底层数组进行批量渲染，提升性能。
   提供激活光栅后端的便捷函数，替换 *merge-layer!* 并注册 :raster 图层。"
  (:require
    [top.kzre.krro.canvas.core.canvas.protocol :as cp]
    [top.kzre.krro.canvas.core.core :as c]
    [top.kzre.krro.canvas.raster.util :as util]
    [top.kzre.krro.canvas.core.layer.util :as lu]
    [top.kzre.krro.canvas.core.rect :as rect])
  (:import [top.kzre.krro.canvas.raster RenderLayer Mask]))


(defn- merge-layer-impl
  [^floats data w h source]
  (let [src-data   (:data source)
        blend-mode (util/blend-mode-str (:blend-mode source) :normal)
        opacity    (float (get source :opacity 1.0))
        transform  (get source :transform lu/identity-matrix)
        masked-src (if-let [mask (:mask source)]
                     (if (and (vector? mask) (= :data (first mask)))
                       (Mask/applyMask src-data (second mask))
                       src-data)
                     src-data)]
    (RenderLayer/blendTransformed data masked-src w h transform blend-mode opacity)))


(defn use-raster-merge-layer!
  []
  (alter-var-root #'c/*merge-layer!* (constantly merge-layer-impl)))


(defmethod c/render-layer! :raster
  [layer ^floats data w h]
  (let [canvas      (:canvas layer)
        layer-data  (cp/data canvas)
        blend-mode  (util/blend-mode-str (:blend-mode layer) :normal)
        opacity     (float (get layer :opacity 1.0))
        transform   (get layer :transform lu/identity-matrix)
        masked-data (if-let [mask (:mask layer)]
                      (if (and (vector? mask) (= :data (first mask)))
                        (Mask/applyMask layer-data (second mask))
                        layer-data)
                      layer-data)
        dirty       (cp/dirty-rect canvas)                ;; [x y w h] 或 nil
        dirty-arr   (when dirty
                      (some-> dirty
                              (rect/clip-rect w h)        ;; 裁剪到画布尺寸
                              rect/rect->array))]         ;; 转为 int-array
    (if dirty-arr
      (RenderLayer/blendTransformedDirty data masked-data w h transform blend-mode opacity dirty-arr)
      (RenderLayer/blendTransformed data masked-data w h transform blend-mode opacity))))
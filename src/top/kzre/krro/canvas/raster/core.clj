(ns top.kzre.krro.canvas.raster.core
  "光栅图层实现：通过画布 API 获取底层数组进行批量渲染，提升性能。
   提供激活光栅后端的便捷函数，替换 *merge-layer!* 并注册 :raster 图层。"
  (:require
    [top.kzre.krro.canvas.core.canvas.protocol :as cp]
    [top.kzre.krro.canvas.core.core :as c]
    [top.kzre.krro.canvas.raster.util :as util]
    [top.kzre.krro.canvas.core.layer.util :as lu])
  (:import [top.kzre.krro.canvas.raster RenderLayer Mask]))

;; ── 核心混合实现（用于 *merge-layer!*）──────────────
(defn- merge-layer-impl
  "实现 *merge-layer!* 协议：从 merged 描述中提取数据与属性，调用 Java 混合。"
  [^floats data w h source]
  (let [src-data   (:data source)
        blend-mode (util/blend-mode-str (:blend-mode source) :normal)
        opacity    (float (get source :opacity 1.0))
        transform  (get source :transform lu/identity-matrix)
        ;; 注意：source 中可能也有 mask，但组蒙板通常已在 render-group-node! 前处理，
        ;; 此处为保证完整性，同样处理 mask
        masked-src (if-let [mask (:mask source)]
                     (if (and (vector? mask) (= :data (first mask)))
                       (Mask/applyMask src-data (second mask))
                       src-data)
                     src-data)]
    (RenderLayer/blendTransformed data masked-src w h transform blend-mode opacity)))

;; ── 激活光栅后端 ─────────────────────────────────
(defn use-raster-merge-layer!
  "激活光栅渲染后端：绑定 *merge-layer!* 为基于 RenderLayer 的混合实现。"
  []
  (alter-var-root #'c/*merge-layer!* (constantly merge-layer-impl)))

;; ── :raster 图层的渲染方法 ────────────────────────
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
                      layer-data)]
    (RenderLayer/blendTransformed data masked-data w h transform blend-mode opacity)))
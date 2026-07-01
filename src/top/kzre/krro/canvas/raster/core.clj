(ns top.kzre.krro.canvas.raster.core
  "光栅图层实现：渲染多方法注册、图层创建。
   目标画布固定为 sRGB，图层自有颜色空间在混合前转换为 sRGB。"
  (:require
    [top.kzre.krro.canvas.core.canvas.core :as canvas]
    [top.kzre.krro.canvas.core.canvas.raster :as raster]
    [top.kzre.krro.canvas.core.layer.core :as layer-core]
    [top.kzre.krro.canvas.core.rect :as rect]
    [top.kzre.krro.canvas.raster.spec]                ;; 注册 spec
    [top.kzre.krro.color.blend :as blend]
    [top.kzre.krro.color.composite :as comp]))

;; ── 光栅图层渲染多方法实现 ──────────────────
(defmethod layer-core/render-layer! :raster
  [layer target-canvas & {:keys [dx dy] :or {dx 0 dy 0}}]
  (let [canvas       (:canvas layer)          ;; 提取内嵌画布
        {:keys [opacity blend-mode visible? color-space]} layer
        layer-space  (or color-space :rgba)
        blend-fn     (fn [dst-rgb src-rgb]
                       (try (blend/blend dst-rgb src-rgb blend-mode)
                            (catch Exception _ src-rgb)))
        src-w        (canvas/canvas-width canvas)   ;; 从画布获取尺寸
        src-h        (canvas/canvas-height canvas)
        dst-w        (canvas/canvas-width target-canvas)
        dst-h        (canvas/canvas-height target-canvas)
        dirty-rects  (volatile! [])]
    (when (and visible? (some? (:pixels canvas)))
      (doseq [y (range src-h) x (range src-w)]
        (let [tx (+ x dx) ty (+ y dy)]
          (when (and (>= tx 0) (< tx dst-w) (>= ty 0) (< ty dst-h))
            (let [src-pixel (canvas/get-pixel canvas x y)
                  dst-pixel (canvas/get-pixel target-canvas tx ty)
                  ;; 色彩空间转换（灰度 → RGBA）
                  src-rgba   (case layer-space
                               :rgba src-pixel
                               :gray (let [g (first src-pixel)] [g g g 1.0])
                               src-pixel)
                  src-rgb    (take 3 src-rgba)
                  src-alpha  (* (nth src-rgba 3 1.0) opacity)]
              (when (pos? src-alpha)
                (let [dst-rgb   (take 3 dst-pixel)
                      blended-rgb (blend-fn (vec dst-rgb) (vec src-rgb))
                      out-color (comp/over (vec dst-pixel)
                                           [(first blended-rgb)
                                            (second blended-rgb)
                                            (nth blended-rgb 2)
                                            src-alpha])]
                  (canvas/set-pixel! target-canvas tx ty out-color)
                  (vswap! dirty-rects conj [tx ty 1 1]))))))))
    ;; 使用统一的矩形工具合并脏矩形
    (rect/merge-rects @dirty-rects)))

;; ── 图层构造 ──────────────────────────────────
(defn make-raster-layer
  [{:keys [id name width height bits-per-channel
           color opacity blend-mode visible? locked? color-space]
    :or   {name             "Raster Layer"
           bits-per-channel 8
           opacity          1.0
           blend-mode       :normal
           visible?         true
           locked?          false
           color-space      :rgba}}]
  (let [id (or id (keyword (str "layer-" (java.util.UUID/randomUUID))))
        channels (case color-space
                   :rgba 4
                   :gray 1
                   (throw (IllegalArgumentException.
                            (str "Unsupported color-space: " color-space))))
        _ (when (and color (not= (count color) channels))
            (throw (IllegalArgumentException.
                     (str "Color vector length " (count color)
                          " does not match color-space " color-space
                          " (expected " channels " channels)"))))
        canvas-map (raster/make-raster-canvas
                     width height bits-per-channel channels
                     :color (or color (repeat channels 0.0)))]
    {:id          id
     :type        :raster
     :name        name
     :opacity     (float opacity)
     :blend-mode  blend-mode
     :visible?    visible?
     :locked?     locked?
     :color-space color-space
     :canvas      canvas-map}))
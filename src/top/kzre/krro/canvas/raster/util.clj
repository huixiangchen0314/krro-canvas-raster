(ns top.kzre.krro.canvas.raster.util
  (:require [top.kzre.krro.canvas.core.layer.core :as lc])
  (:import (top.kzre.krro.canvas.core.layer MathUtils)))


(defn ->string
  "将 keyword 或字符串转为字符串。nil 返回 nil。"
  [x]
  (when x (name x)))

(defn blend-mode-str
  "从 map 中提取 :blend-mode 并转为字符串，缺失返回默认字符串。"
  [m default]
  (->string (get m :blend-mode default)))

(defn world->local
  "将世界坐标 (wx, wy) 转换为光栅图层的本地坐标 (lx, ly)。
   若图层包含 :transform (世界变换矩阵)，则使用其逆矩阵；否则回退到从图层属性计算逆变换。"
  [layer wx wy]
  (if-let [^floats world-matrix (:transform layer)]
    ;; 有预处理的世界矩阵，求逆并变换点
    (if-let [inv-matrix (MathUtils/invert world-matrix)]
      (lc/transform-point inv-matrix wx wy)
      ;; 若矩阵不可逆，回退到属性计算
      (let [inv-matrix2 (lc/compose-inverse-transform layer)]
        (lc/transform-point inv-matrix2 wx wy)))
    ;; 否则从图层属性直接计算逆矩阵
    (let [inv-matrix (lc/compose-inverse-transform layer)]
      (lc/transform-point inv-matrix wx wy))))

(defn local->array-index
  "将图层本地坐标 (lx, ly) 转换为像素数组索引 (px, py)。
   origin-x/origin-y 缺省为 0。返回的索引可能为负（点在数组范围外）。"
  [layer lx ly]
  (let [ox (or (:origin-x layer) 0)
        oy (or (:origin-y layer) 0)]
    {:x (long (- lx ox))
     :y (long (- ly oy))}))

(ns top.kzre.krro.canvas.raster.test-utils
  "光栅图层测试辅助函数。"
  (:require [clojure.test :refer :all]))

(defn approx?
  "检查两个浮点数是否在给定的 epsilon 范围内相等。"
  ([expected actual]
   (approx? expected actual 0.0001))
  ([expected actual epsilon]
   (<= (Math/abs (- expected actual)) epsilon)))
(ns top.kzre.krro.canvas.raster.util)


(defn ->string
  "将 keyword 或字符串转为字符串。nil 返回 nil。"
  [x]
  (when x (name x)))

(defn blend-mode-str
  "从 map 中提取 :blend-mode 并转为字符串，缺失返回默认字符串。"
  [m default]
  (->string (get m :blend-mode default)))

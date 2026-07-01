(ns top.kzre.krro.canvas.raster.core-test
  (:require [clojure.test :refer :all]
            [top.kzre.krro.canvas.raster.core :as raster]
            [top.kzre.krro.canvas.core.canvas.core :as canvas]
            [top.kzre.krro.canvas.core.layer.core :as layer-core]
            [top.kzre.krro.canvas.raster.test-utils :refer [approx?]]
            [clojure.spec.alpha :as s]
            [top.kzre.krro.canvas.raster.spec]
            [top.kzre.krro.canvas.core.layer.spec :as layer-spec]))

(defn- make-white-target [w h]
  (let [target (canvas/make-canvas :raster :width w :height h :bits-per-channel 8)]
    (doseq [y (range h) x (range w)]
      (canvas/set-pixel! target x y [1.0 1.0 1.0 1.0]))
    target))

(deftest make-raster-layer-test
  (testing "default layer properties"
    (let [layer (raster/make-raster-layer {:id :test-layer
                                           :width 4 :height 4
                                           :bits-per-channel 8})]
      (is (= :test-layer (:id layer)))
      (is (= :raster (:type layer)))
      (is (= "Raster Layer" (:name layer)))
      (is (= 1.0 (:opacity layer)))
      (is (= :normal (:blend-mode layer)))
      (is (true? (:visible? layer)))
      (is (false? (:locked? layer)))
      (is (= :rgba (:color-space layer)))
      (is (s/valid? ::layer-spec/layer layer))))

  (testing "custom properties (gray)"
    (let [layer (raster/make-raster-layer {:id :gray-custom
                                           :width 2 :height 2
                                           :bits-per-channel 16
                                           :opacity 0.5
                                           :blend-mode :multiply
                                           :visible? false
                                           :locked? true
                                           :color-space :gray
                                           :color [0.3]})]
      (is (= 0.5 (:opacity layer)))
      (is (= :multiply (:blend-mode layer)))
      (is (false? (:visible? layer)))
      (is (true? (:locked? layer)))
      (is (= :gray (:color-space layer)))
      (is (s/valid? ::layer-spec/layer layer)))))

(deftest layer-pixel-content-test
  (let [layer (raster/make-raster-layer {:id :pixels
                                         :width 2 :height 2
                                         :bits-per-channel 8
                                         :color [1.0 0.0 0.0 1.0]})
        c (:canvas layer)]
    (is (= [1.0 0.0 0.0 1.0] (canvas/get-pixel c 0 0)))
    (is (= [1.0 0.0 0.0 1.0] (canvas/get-pixel c 1 1)))))

(deftest render-layer-basic-test
  (let [layer (raster/make-raster-layer {:id :fg
                                         :width 2 :height 2
                                         :bits-per-channel 8
                                         :color [0.0 1.0 0.0 0.5]})
        target (make-white-target 4 4)
        dirty (layer-core/render-layer! layer target)]
    (let [px (canvas/get-pixel target 0 0)]
      (is (approx? 0.5 (first px) 0.01))
      (is (approx? 1.0 (second px) 0.01))
      (is (approx? 0.5 (nth px 2) 0.01))
      (is (approx? 1.0 (nth px 3) 0.01)))
    (is (seq dirty))))

(deftest render-layer-invisible-test
  (let [layer (raster/make-raster-layer {:id :invisible
                                         :width 2 :height 2
                                         :visible? false
                                         :bits-per-channel 8
                                         :color [1.0 0.0 0.0 1.0]})
        target (canvas/make-canvas :raster :width 4 :height 4 :bits-per-channel 8)
        dirty (layer-core/render-layer! layer target)]
    (is (empty? dirty))
    (is (= [0.0 0.0 0.0 0.0] (canvas/get-pixel target 0 0)))))

(deftest render-layer-offset-test
  (let [layer (raster/make-raster-layer {:id :offset
                                         :width 2 :height 2
                                         :bits-per-channel 8
                                         :color [1.0 0.0 0.0 1.0]})
        target (canvas/make-canvas :raster :width 4 :height 4 :bits-per-channel 8)
        _ (layer-core/render-layer! layer target :dx 2 :dy 2)]
    (let [px (canvas/get-pixel target 2 2)]
      (is (approx? 1.0 (first px) 0.01))
      (is (approx? 0.0 (second px) 0.01))
      (is (approx? 0.0 (nth px 2) 0.01))
      (is (approx? 1.0 (nth px 3) 0.01)))
    (is (= [0.0 0.0 0.0 0.0] (canvas/get-pixel target 0 0)))))

(deftest render-layer-gray-test
  (let [layer (raster/make-raster-layer {:id :gray
                                         :width 2 :height 2
                                         :bits-per-channel 8
                                         :color-space :gray
                                         :color [0.5]
                                         :opacity 1.0})
        target (make-white-target 4 4)
        _ (layer-core/render-layer! layer target)]
    (let [px (canvas/get-pixel target 0 0)]
      (is (approx? 0.5 (first px) 0.01))
      (is (approx? 0.5 (second px) 0.01))
      (is (approx? 0.5 (nth px 2) 0.01))
      (is (approx? 1.0 (nth px 3) 0.01)))))

(deftest render-layer-blend-mode-test
  (let [layer (raster/make-raster-layer {:id :multiply-layer
                                         :width 2 :height 2
                                         :bits-per-channel 8
                                         :color [0.5 0.5 0.5 1.0]
                                         :blend-mode :multiply
                                         :opacity 1.0})
        target (make-white-target 4 4)
        _ (layer-core/render-layer! layer target)]
    (let [px (canvas/get-pixel target 0 0)]
      (is (approx? 0.5 (first px) 0.01))
      (is (approx? 0.5 (second px) 0.01))
      (is (approx? 0.5 (nth px 2) 0.01))
      (is (approx? 1.0 (nth px 3) 0.01)))))

(deftest render-layer-opacity-test
  (let [layer (raster/make-raster-layer {:id :opacity-layer
                                         :width 2 :height 2
                                         :bits-per-channel 8
                                         :color [1.0 0.0 0.0 0.5]
                                         :opacity 0.5})
        target (make-white-target 4 4)
        _ (layer-core/render-layer! layer target)]
    (let [px (canvas/get-pixel target 0 0)]
      (is (approx? 1.0 (first px) 0.01))
      (is (approx? 0.75 (second px) 0.01))
      (is (approx? 0.75 (nth px 2) 0.01))
      (is (approx? 1.0 (nth px 3) 0.01)))))

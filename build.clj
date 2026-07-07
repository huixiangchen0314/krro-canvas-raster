(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'top.kzre/krro-canvas-raster)
(def version "0.1.0")
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file "target/krro-canvas-raster-0.1.0.jar")            ;; 硬编码，与 Makefile 一致
(def uber-file "target/krro-canvas-raster-0.1.0-standalone.jar")

(defn clean [_]
      (b/delete {:path "target"}))

(defn compile-java [_]
      (b/javac {:src-dirs ["src"]
                :class-dir class-dir
                :basis basis}))

(defn jar [_]
      (clean nil)
      (compile-java nil)
      (b/write-pom {:class-dir class-dir
                    :lib lib
                    :version version
                    :basis basis
                    :src-dirs ["src"]
                    :scm {:url "https://github.com/topkzre/krro-canvas-raster"
                          :connection "scm:git:git://github.com/topkzre/krro-canvas-raster.git"
                          :developerConnection "scm:git:ssh://git@github.com:topkzre/krro-canvas-raster.git"}})
      (b/copy-dir {:src-dirs ["src" "resources"] :target-dir class-dir})
      (b/jar {:class-dir class-dir
              :jar-file jar-file})
      (println "Jar created:" jar-file))

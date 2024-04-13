(ns build
  (:require [clojure.tools.build.api :as b]))

(def build-folder "target")
(def jar-content (str build-folder "/classes"))     ; folder where we collect files to pack in a jar

(def lib-name 'fi.metosin/oksa)
(def version "0.1.0")
(def is-release (Boolean/parseBoolean (System/getenv "RELEASE")))
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file-name (format "%s/%s.jar" build-folder (name lib-name)))

(defn clean [_]
  (b/delete {:path build-folder})
  (println (format "Build folder \"%s\" removed" build-folder)))

(defn jar [_]
  (clean nil)
  (b/copy-dir {:src-dirs   ["src"]
               :target-dir jar-content})
  (b/write-pom {:class-dir jar-content
                :lib       lib-name
                :version   (if is-release
                             version
                             (str version "-SNAPSHOT"))
                :basis     basis
                :src-dirs  ["src"]
                :scm {:url "https://github.com/metosin/oksa"
                      :connection "scm:git:git://github.com/metosin/oksa/oksa.git"
                      :tag (if is-release
                             version
                             (b/git-process {:git-args "rev-parse HEAD"}))}})
  (b/jar {:class-dir jar-content
          :jar-file jar-file-name})
  (println (format "Jar file created: \"%s\"" jar-file-name)))

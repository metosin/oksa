(ns build
  (:require [clojure.tools.build.api :as b]))

(def build-folder "target")
(def jar-content (str build-folder "/classes"))     ; folder where we collect files to pack in a jar

(def lib-name 'fi.metosin/oksa)
(def version "0.0.1-SNAPSHOT")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file-name (format "%s/%s-%s.jar" build-folder (name lib-name) version))

(defn clean [_]
  (b/delete {:path build-folder})
  (println (format "Build folder \"%s\" removed" build-folder)))

(defn jar [_]
  (clean nil)
  (b/copy-dir {:src-dirs   ["src"]
               :target-dir jar-content})
  (b/write-pom {:class-dir jar-content
                :lib       lib-name
                :version   version
                :basis     basis
                :src-dirs  ["src"]})
  (b/jar {:class-dir jar-content
          :jar-file  jar-file-name})
  (println (format "Jar file created: \"%s\"" jar-file-name)))

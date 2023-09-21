(ns oksa.core
  (:require [oksa.parse :as parse]
            [oksa.unparse :as unparse]))

(defn unparse
  [x]
  (-> (parse/to-ast x)
      (unparse/unparse)))

(defn explain
  [x]
  (parse/explain x))

(ns oksa.core
  (:require [oksa.alpha.api :as api]
            [oksa.parse :as parse]
            [oksa.unparse :as unparse]))

(defn explain
  "Explains `x` using `malli.core/explain`.

  Expects `x` to be a malli-like vector."
  [x]
  (parse/explain x))

(defn unparse
  "Attempts to parse `x` and produce a GraphQL request string.

  Expects `x` to be a malli-like vector."
  [x]
  (-> (parse/to-ast x)
      (unparse/unparse)))

(defn gql
  "Attempts to produce a GraphQL request string from `objs`.

  Expects `objs` to either be instance(s) of `oksa.alpha.protocol/Representable` or malli-like vector(s).

  See `oksa.alpha.api/api`, `README.md`, or tests for examples."
  [& objs]
  (apply str
         (clojure.string/join
          (System/lineSeparator)
          (map (fn [obj]
                 (if (satisfies? oksa.alpha.protocol/Representable obj)
                   (api/gql obj)
                   (unparse obj)))
               objs))))

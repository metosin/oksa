(ns oksa.core
  (:require [oksa.alpha.api :as api]
            [oksa.parse :as parse]
            [oksa.unparse :as unparse]
            [oksa.alpha.protocol :as protocol]))

(defn explain
  "Explains `x` using `malli.core/explain`.

  Expects `x` to be a malli-like vector."
  [x]
  (parse/explain x))

(defn unparse
  "Attempts to parse `x` and produce a GraphQL request string.

  `opts` is an (optional) map and uses the following fields here:

  | field           | description                                                                      |
  |-----------------|----------------------------------------------------------------------------------|
  | `:oksa/name-fn` | A function that accepts a single arg `name` and expects a stringifiable output.  |
  |                 | Applied recursively to all fields & selections.                                  |

  Expects `x` to be a malli-like vector."
  ([x]
   (unparse nil x))
  ([opts x]
   (protocol/-gql (parse/to-ast x) opts)))

(defn gql*
  "Attempts to produce a GraphQL request string from `objs`.

  Expects `objs` to either be instance(s) of `oksa.alpha.protocol/Representable` or malli-like vector(s).

  `opts` is a map and uses the following fields here:

  | field           | description                                                                      |
  |-----------------|----------------------------------------------------------------------------------|
  | `:oksa/name-fn` | A function that accepts a single arg `name` and expects a stringifiable output.  |
  |                 | Applied recursively to all fields & selections.                                  |

  See `oksa.alpha.api/api`, `README.md`, or tests for examples."
  [opts & objs]
  (apply str
         (clojure.string/join
           #?(:clj  (System/lineSeparator)
              :cljs (with-out-str (newline)))
           (map (fn [obj]
                  (if (satisfies? protocol/Representable obj)
                    (api/gql opts obj)
                    (unparse opts obj)))
                objs))))

(defn gql
  "Attempts to produce a GraphQL request string from `objs`.

  Expects `objs` to either be instance(s) of `oksa.alpha.protocol/Representable` or malli-like vector(s).

  See `oksa.alpha.api/api`, `README.md`, or tests for examples."
  [& objs]
  (apply gql* nil objs))

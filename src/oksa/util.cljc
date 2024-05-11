(ns oksa.util
  (:require [malli.core :as m]))

#?(:cljs (goog-define mode "default")
   :clj  (def ^{:doc "Modes `default` and `debug` supported."}
           mode (as-> (or (System/getProperty "oksa.api/mode") "default") $ (.intern $))))

(defn transform-malli-ast
  "Applies transform-map to parse-tree recursively. Adapted from `instaparse.core/hiccup-transform`."
  [transform-map parse-tree]
  (cond
    (and (sequential? parse-tree)
         (sequential? (first parse-tree)))
    (map (partial transform-malli-ast transform-map) parse-tree)

    (and (sequential? parse-tree) (seq parse-tree))
    (if-let [transform (transform-map (first parse-tree))]
      (apply transform (map (partial transform-malli-ast transform-map)
                            (next parse-tree)))
      (into [(first parse-tree)]
            (map (partial transform-malli-ast transform-map)
                 (next parse-tree))))

    :else
    parse-tree))

(defn -parse-or-throw
  [type form parser message]
  (let [retval (parser form)]
    (if (not= retval :malli.core/invalid)
      retval
      (throw (ex-info message
                      (cond
                        (= mode "debug") {:malli.core/explain (malli.core/explain
                                                                (oksa.parse/-graphql-dsl-lang type)
                                                                form)}
                        (= mode "default") {}
                        :else (throw (ex-info "incorrect `oksa.api/mode` (system property), expected one of `default` or `debug`" {:mode mode}))))))))
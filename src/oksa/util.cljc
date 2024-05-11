(ns oksa.util)

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

(def -name-pattern "[_A-Za-z][_0-9A-Za-z]*")
(def re-name (re-pattern -name-pattern))
(def re-variable-name (re-pattern (str "[$]?" -name-pattern)))
(def re-type-name (re-pattern (str -name-pattern "[!]?")))
(def re-fragment-name (re-pattern (str "(?!on)" -name-pattern)))
(def re-variable-reference (re-pattern (str "[$]" -name-pattern)))
(def re-enum-value (re-pattern (str "(?!(true|false|null))" -name-pattern)))
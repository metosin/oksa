(ns oksa.util
  (:require [malli.core :as m]))

(defn -oksa-parse?
  [ast]
  (boolean (and (map? ast)
                (:oksa.parse/node ast))))

(defn transform-to-malli-ast
  [ast]
  (prn ::ast ast)
  (cond
    (and (m/tag? ast) (m/tag? (:value ast)))
    (do (prn ::is-tag-and-value-is-tag ast)
        (into [(:key ast)]
              [(transform-to-malli-ast (:value ast))]))

    (and (m/tag? ast)
         (sequential? (:value ast))
         (m/tag? (first (:value ast))))
    (do (prn ::is-tag-and-value-is-sequential-tag ast)
        (into [(:key ast)]
              [(transform-to-malli-ast (:value ast))]))

    (and (m/tag? ast)
         (sequential? (:value ast))
         (m/tags? (first (:value ast))))
    (do (prn ::is-tag-and-value-is-sequential-tags ast)
        (into [(:key ast)]
              [(mapcat transform-to-malli-ast (:value ast))]))

    (m/tag? ast)
    (do
      (prn ::is-tag ast)
      (into [(:key ast)]
            [(:value ast)]))

    (m/tags? ast)
    (do (prn ::is-tags ast)
        (mapv transform-to-malli-ast [(:values ast)]))

    (-oksa-parse? ast)
    (do
      (prn ::is-oksa-parse ast)
      {:oksa.parse/node (transform-to-malli-ast (:oksa.parse/node ast))
      :oksa.parse/children (not-empty (mapv transform-to-malli-ast (:oksa.parse/children ast)))})))

(defn transform-malli-ast
  "Applies transform-map to parse-tree recursively. Adapted from `instaparse.core/hiccup-transform`."
  [transform-map parse-tree]
  (prn ::Parse-tree parse-tree)
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

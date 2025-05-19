(ns oksa.util
  (:require [malli.core :as m]))

(defn -oksa-parse?
  [ast]
  (boolean (and (map? ast)
                (:oksa.parse/node ast))))

(defn third
  [coll]
  (first (rest (rest coll))))

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
         (m/tag? (third (:value ast))))
    (let [value (:value ast)]
      (prn ::is-tag-and-value-is-sequential-containing-tag ast)
      (into [(:key ast)]
            [(doall (mapcat transform-to-malli-ast [value]))]))

    (and (m/tag? ast)
         (sequential? (:value ast))
         (sequential? (third (:value ast))))
    (let [value (:value ast)]
      (prn ::is-tag-and-value-is-sequential-containing-sequence)
      (into [(:key ast)]
            [(transform-to-malli-ast value)]))

    (and (m/tag? ast)
         (sequential? (:value ast))
         (m/tags? (first (:value ast))))
    (do (prn ::is-tag-and-value-is-sequential-tags ast)
        (into [(:key ast)]
              [(doall (mapcat transform-to-malli-ast (:value ast)))]))

    (and (sequential? ast)
         (m/tag? (first ast)))
    (map transform-to-malli-ast ast)

    (m/tag? ast)
    (do
      (prn ::is-tag ast)
      (into [(:key ast)]
            [(:value ast)]))

    (m/tags? ast)
    (do (prn ::is-tags ast)
        (mapv transform-to-malli-ast [(:values ast)]))

    (and (-oksa-parse? ast)
         (m/tag? (:oksa.parse/children ast)))
    (do
      (prn ::is-oksa-parse-and-children-is-tag ast)
      {:oksa.parse/node (transform-to-malli-ast (:oksa.parse/node ast))
       :oksa.parse/children (transform-to-malli-ast (:oksa.parse/children ast))})

    (-oksa-parse? ast)
    (do
      (prn ::is-oksa-parse ast)
      {:oksa.parse/node (transform-to-malli-ast (:oksa.parse/node ast))
       :oksa.parse/children (not-empty (mapv transform-to-malli-ast (:oksa.parse/children ast)))})

    (and (sequential? ast)
         (m/tag? (third ast)))
    (let [[key opts child] ast]
      (prn ::is-sequential-and-child-has-tag ast)
      (into [key opts]
            [(doall (mapcat transform-to-malli-ast [child]))]))

    (and (sequential? ast)
         (m/tag? (first (third ast))))
    (let [[key opts children] ast]
      (prn ::is-sequential-and-first-child-has-tag ast)
      (into [key opts]
            [(mapv transform-to-malli-ast children)]))))

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

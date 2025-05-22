(ns oksa.util
  (:require [malli.core :as m]))

(defn malli-tag-supported?
  []
  (boolean (resolve 'malli.core/tag?)))

(defn -oksa-parse?
  [ast]
  (boolean (and (map? ast)
                (:oksa.parse/node ast))))

(defn- third
  [coll]
  (first (rest (rest coll))))

(defn -non-null-list-type?
  [coll]
  (= (first coll) :!))

(defn enlive->hiccup
  "Walks & transforms malli 0.18.0 parse (\"enlive\") result to pre-0.18.0 AST (\"hiccup\") format."
  [ast]
  (cond
    (and (m/tag? ast)
         (m/tag? (:value ast)))
    (into [(:key ast)] [(enlive->hiccup (:value ast))])

    (and (m/tag? ast)
         (sequential? (:value ast))
         (m/tag? (first (:value ast))))
    (into [(:key ast)] [(enlive->hiccup (:value ast))])

    (and (m/tag? ast)
         (sequential? (:value ast))
         (m/tag? (third (:value ast))))
    (into [(:key ast)] [(doall (mapcat enlive->hiccup [(:value ast)]))])

    (and (m/tag? ast)
         (sequential? (:value ast))
         (sequential? (first (:value ast))))
    (into [(:key ast)] [(mapv enlive->hiccup (:value ast))])

    (and (m/tag? ast)
         (sequential? (:value ast))
         (sequential? (third (:value ast))))
    (into [(:key ast)] [(enlive->hiccup (:value ast))])

    (and (m/tag? ast)
         (sequential? (:value ast))
         (m/tags? (first (:value ast))))
    (into [(:key ast)] [(doall (mapcat enlive->hiccup (:value ast)))])

    (and (m/tag? ast)
         (sequential? (:value ast))
         (-non-null-list-type? (:value ast)))
    (into [(:key ast)] [(mapv enlive->hiccup (:value ast))])

    (m/tag? ast)
    (into [(:key ast)] [(:value ast)])

    (m/tags? ast)
    (mapv enlive->hiccup [(:values ast)])

    (and (-oksa-parse? ast)
         (m/tag? (:oksa.parse/children ast)))
    {:oksa.parse/node (enlive->hiccup (:oksa.parse/node ast))
     :oksa.parse/children (enlive->hiccup (:oksa.parse/children ast))}

    (-oksa-parse? ast)
    {:oksa.parse/node (enlive->hiccup (:oksa.parse/node ast))
     :oksa.parse/children (not-empty (mapv enlive->hiccup (:oksa.parse/children ast)))}

    (and (sequential? ast)
         (m/tag? (first ast)))
    (mapv enlive->hiccup ast)

    (and (sequential? ast)
         (m/tag? (third ast)))
    (let [[key opts child] ast]
      (into [key opts] [(doall (mapcat enlive->hiccup [child]))]))

    (and (sequential? ast)
         (m/tag? (first (third ast))))
    (let [[key opts children] ast]
      (into [key opts] [(mapv enlive->hiccup children)]))

    (and (sequential? ast)
         (sequential? (first ast)))
    (mapv enlive->hiccup ast)

    (and (sequential? ast)
         (seq ast))
    (into [(first ast)] (mapv enlive->hiccup (next ast)))

    :else ast))

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

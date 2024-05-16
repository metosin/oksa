(ns oksa.unparse
  (:require [clojure.string :as str]
            [oksa.alpha.protocol :as protocol]
            [oksa.util :as util])
  #?(:clj (:import (clojure.lang Keyword PersistentVector PersistentArrayMap))))

(defmulti format-value type)

(defn serialize
  [opts x]
  (protocol/-unparse x opts))

(declare format-type)

(defn -format-list
  [child opts]
  (str "[" (serialize opts child) "]" (when (:non-null opts) "!")))

(defn- format-type
  [type opts]
  (protocol/-unparse type opts))

(defn -variable-name
  [variable]
  (str "$" (str/replace (name variable) #"^\$" "")))

(declare format-directives)

(defn -format-variable-definition
  [variable opts type]
  (str (-variable-name variable)
       ":"
       (format-type type opts)
       (when (contains? opts :default)
         (protocol/-unparse (:default opts) opts))
       (when (:directives opts)
         (str " " (protocol/-unparse (:directives opts) opts)))))

(defn- format-variable-definitions
  [opts]
  (str "("
       (str/join ","
                 (map (fn [variable-definition]
                        (protocol/-unparse variable-definition opts))
                      (:variables opts)))
       ")"))

(declare -format-arguments)

(defn format-directive
  [directive-name opts]
  (str "@"
       (name directive-name)
       (when (:arguments opts) (protocol/-unparse (:arguments opts) opts))))

(defn format-directives
  [opts directives]
  (str/join " " (map #(protocol/-unparse % opts) directives)))

(defn- escape-string
  [s]
  (str/escape s {\" "\\\""
                 \\ "\\\\"
                 \u0008 "\\b"
                 \u0009 "\\t"
                 \u000A "\\n"
                 \u000B "\\u000B"
                 \u000C "\\f"
                 \u000D "\\r"}))

(defmethod format-value #?(:clj Number
                           :cljs js/Number) [x] (str x))
#?(:clj (defmethod format-value clojure.lang.Ratio [x] (str (double x))))
(defmethod format-value #?(:clj String
                           :cljs js/String) [s] (str "\"" (escape-string s) "\""))
(defmethod format-value #?(:clj Boolean
                           :cljs js/Boolean) [x] (str x))
(defmethod format-value nil [_] "null")
(defmethod format-value #?(:clj  Keyword
                           :cljs cljs.core/Keyword) [x]
  (let [enum-or-variable (name x)]
    (when-let [enum (re-matches #"^[^$].*$" enum-or-variable)]
      (assert (not (#{"true" "false" "null"} enum)) "invalid name"))
    enum-or-variable))
(defmethod format-value #?(:clj PersistentVector
                           :cljs cljs.core/PersistentVector)
  [x]
  (str "[" (clojure.string/join " " (mapv format-value x)) "]"))
(defmethod format-value #?(:clj PersistentArrayMap
                           :cljs cljs.core/PersistentArrayMap)
  [x]
  (str "{"
       (str/join ", " (map (fn [[object-field-name object-field-value]]
                             (str (name object-field-name) ":" (format-value object-field-value))) x))
       "}"))

(defn -format-arguments
  [opts arguments]
  (str "(" (str/join ", " (map #(protocol/-unparse % opts) arguments)) ")"))

(defn unparse-document
  [opts xs]
  (str/join #?(:clj  (System/lineSeparator)
               :cljs (with-out-str (newline)))
            (map (partial serialize opts) xs)))

(defn unparse-field
  ([name opts]
   (unparse-field name opts nil))
  ([name {:keys [alias arguments directives] :as opts} xs]
   (let [name-fn (:oksa/name-fn opts)
         field-name (clojure.core/name name)]
     (str (when (some? alias) (protocol/-unparse alias opts))
          (util/-validate-re-pattern util/re-name
                                     (if name-fn
                                       (name-fn field-name)
                                       field-name)
                                     "invalid name")
          (when (and (some? arguments)
                     (not-empty (protocol/-form arguments)))
            (protocol/-unparse arguments opts))
          (when directives (protocol/-unparse directives opts))
          (when (some? xs) (apply str (serialize opts xs)))))))

(defn none?
  [f coll]
  (every? (complement f) coll))

(defn unparse-selection-set
  [opts selections]
  (assert (or (and (every? #(satisfies? protocol/AST %) selections)
                   (every? #(satisfies? protocol/Serializable %) selections))
              (none? #(satisfies? protocol/AST %) selections)))
  (if (satisfies? protocol/AST (first selections))
    (str "{"
         (apply str (loop [acc []
                           rst selections]
                      (if (seq rst)
                        (let [itm (first rst)
                              lookahead (second rst)]
                          (recur (cond-> (conj acc (protocol/-unparse itm opts))
                                   (and (satisfies? protocol/AST lookahead)
                                        (not= (protocol/-type lookahead)
                                              :oksa.parse/SelectionSet)) (conj " "))
                                 (rest rst)))
                        acc)))
         "}")
    (str "{"
         (str/join " " selections)
         "}")))

(defn unparse-operation-definition
  ([operation-type opts]
   (unparse-operation-definition operation-type opts nil))
  ([operation-type opts & xs]
   (assert (#{"query" "mutation" "subscription"} operation-type)
           "invalid operation-type")
   (str operation-type
        " "
        (when (:name opts) (str (protocol/-unparse (:name opts) opts) " "))
        (when (:variables opts) (format-variable-definitions opts))
        (when (:directives opts) (protocol/-unparse (:directives opts) opts))
        (apply str (map (partial serialize opts) xs)))))

(defn unparse-fragment-definition
  ([opts]
   (unparse-fragment-definition opts nil))
  ([opts & xs]
   (str "fragment "
        (protocol/-unparse (:name opts) opts)
        " "
        (when (:on opts) (protocol/-unparse (:on opts) opts))
        (when (:directives opts) (protocol/-unparse (:directives opts) opts))
        (apply str (map (partial serialize opts) xs)))))

(defn unparse-fragment-spread
  [opts]
  (let [name-fn (:oksa/name-fn opts)
        fragment-spread-name (name (:name opts))]
    (str "..."
         (util/-validate-re-pattern util/re-name
                                    (if name-fn
                                      (name-fn fragment-spread-name)
                                      fragment-spread-name)
                                    "invalid name")
         (when (:directives opts) (protocol/-unparse (:directives opts) opts)))))

(defn unparse-inline-fragment
  ([opts]
   (unparse-inline-fragment opts nil))
  ([opts xs]
   (str "..."
        (when (:on opts) (protocol/-unparse (:on opts) opts))
        (when (:directives opts) (protocol/-unparse (:directives opts) opts))
        (apply str (serialize opts xs)))))

(def -unparse-xf
  {:document (fn [opts & xs]
               (unparse-document opts xs))
   :fragment unparse-fragment-definition
   :query (partial unparse-operation-definition "query")
   :mutation (partial unparse-operation-definition "mutation")
   :subscription (partial unparse-operation-definition "subscription")
   :field (fn [opts & xs]
            (unparse-field (:name opts) opts xs))
   :selection (fn [_opts & xs]
                (apply str xs))
   :selectionset (fn [opts & xs]
                   (unparse-selection-set opts xs))
   :fragment-spread (fn [opts & _xs]
                      (unparse-fragment-spread opts))
   :inline-fragment (fn [opts & xs]
                      (unparse-inline-fragment opts xs))})

(defn unparse
  [ast]
  (util/transform-malli-ast -unparse-xf ast))

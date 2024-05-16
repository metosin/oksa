(ns oksa.unparse
  (:require [clojure.string :as str]
            [oksa.alpha.protocol :as protocol]
            [oksa.util :as util])
  #?(:clj (:import (clojure.lang Keyword PersistentVector PersistentArrayMap))))

(defmulti format-value type)

(declare format-type)

(defn -format-list
  [child opts]
  (str "[" (protocol/-unparse child opts) "]" (when (:non-null opts) "!")))

(defn- format-type
  [type opts]
  )

(defn -variable-name
  [variable]
  (str "$" (str/replace (name variable) #"^\$" "")))

(declare format-directives)

(defn -format-variable-definition
  [variable opts type]
  (str (-variable-name variable)
       ":"
       (protocol/-unparse type opts)
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

(defn -format-argument
  [name value]
  (str (clojure.core/name name) ":" (oksa.unparse/format-value value)))

(defn -format-arguments
  [opts arguments]
  (str "(" (str/join ", " (map #(protocol/-unparse % opts) arguments)) ")"))

(defn unparse-document
  [opts xs]
  (str/join #?(:clj  (System/lineSeparator)
               :cljs (with-out-str (newline)))
            (map #(protocol/-unparse % opts) xs)))

(defn unparse-field
  [name {:keys [alias arguments directives] :as opts} xs]
  (str (when (some? alias) (protocol/-unparse alias opts))
       (clojure.core/name name)
       (when (and (some? arguments)
                  (not-empty (protocol/-form arguments)))
         (protocol/-unparse arguments opts))
       (when directives (protocol/-unparse directives opts))
       (when (some? xs) (apply str (protocol/-unparse xs opts)))))

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
        (apply str (map #(protocol/-unparse % opts) xs)))))

(defn unparse-fragment-definition
  ([opts]
   (unparse-fragment-definition opts nil))
  ([opts & xs]
   (str "fragment "
        (protocol/-unparse (:name opts) opts)
        " "
        (when (:on opts) (protocol/-unparse (:on opts) opts))
        (when (:directives opts) (protocol/-unparse (:directives opts) opts))
        (apply str (map #(protocol/-unparse % opts) xs)))))

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
        (apply str (protocol/-unparse xs opts)))))

(defn format-on
  [name]
  (str "on " name))

(defn format-alias
  [alias]
  (str alias ":"))

(defn format-default
  [value]
  (str "=" (format-value value)))

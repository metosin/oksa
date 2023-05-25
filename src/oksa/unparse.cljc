(ns oksa.unparse
  (:require [clojure.string :as str]
            [oksa.util :as util])
  #?(:clj (:import (clojure.lang Keyword PersistentVector PersistentArrayMap))))

(defn- format-type
  [[type opts child]]
  (if (= :oksa/list type)
    (str "[" (format-type child) "]" (when (:non-null opts) "!"))
    (str (name type) (when (:non-null opts) "!"))))

(defn- variable-name
  [variable]
  (str "$" (str/replace (name variable) #"^\$" "")))

(declare format-directives)

(defn- format-variable-definition
  [variable opts type]
  (str (variable-name variable)
       ":"
       (format-type type)
       (when (:default opts)
         (str "=" (:default opts)))
       (when (:directives opts)
         (str " " (format-directives (:directives opts))))))

(defn- format-variable-definitions
  [variable-definitions]
  (str "("
       (str/join ","
                 (map (fn [[variable opts type]]
                        (format-variable-definition variable opts type))
                      variable-definitions))
       ")"))

(declare format-arguments)

(defn- format-directive
  [[directive-name opts :as _directive]]
  (str "@"
       (name directive-name)
       (when (:arguments opts) (format-arguments (:arguments opts)))))

(defn- format-directives
  [directives]
  (str/join " " (map format-directive directives)))

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

(defmulti format-value type)
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
  (str x))
(defmethod format-value #?(:clj PersistentArrayMap
                           :cljs cljs.core/PersistentArrayMap)
  [x]
  (str "{"
       (str/join ", " (map (fn [[object-field-name object-field-value]]
                             (str (name object-field-name) ":" (format-value object-field-value))) x))
       "}"))

(defn- format-argument
  [[argument-name value]]
  (str (name argument-name) ":" (format-value value)))

(defn- format-arguments
  [arguments]
  (str "(" (str/join ", " (map format-argument arguments)) ")"))

(def ^:private unparse-xf
  (letfn [(document
            [_opts & xs]
            (str/join #?(:clj (System/lineSeparator)
                         :cljs (with-out-str (newline)))
                      xs))
          (fragment
            [opts & xs]
            (str "fragment "
                 (name (:name opts))
                 " "
                 (when (:on opts) (str "on " (name (:on opts))))
                 (when (:directives opts) (format-directives (:directives opts)))
                 (apply str xs)))
          (operation
            [operation-type opts & xs]
            (assert (#{"query" "mutation" "subscription"} operation-type)
                    "invalid operation-type")
            (str operation-type
                 " "
                 (when (:name opts) (str (name (:name opts)) " "))
                 (when (:variables opts) (format-variable-definitions (:variables opts)))
                 (when (:directives opts) (format-directives (:directives opts)))
                 (apply str xs)))]
    {:document document
     :fragment fragment
     :query (partial operation "query")
     :mutation (partial operation "mutation")
     :subscription (partial operation "subscription")
     :field (fn [opts & xs]
              (str (when (:alias opts) (str (name (:alias opts)) ":"))
                   (name (:name opts))
                   (when (and (some? (:arguments opts))
                              (not-empty (:arguments opts)))
                     (format-arguments (:arguments opts)))
                   (when (:directives opts) (format-directives (:directives opts)))
                   (apply str xs)))
     :selection (fn [_opts & xs]
                  (apply str xs))
     :selectionset (fn [_opts & xs]
                     (str "{"
                          (str/join " " xs)
                          "}"))
     :fragment-spread (fn [opts & _xs]
                        (str "..."
                             (name (:name opts))
                             (when (:directives opts) (format-directives (:directives opts)))))
     :inline-fragment (fn [opts & xs]
                        (str "..."
                             (when (:on opts) (str "on " (name (:on opts))))
                             (when (:directives opts) (format-directives (:directives opts)))
                             (apply str xs)))}))

(defn unparse
  [ast]
  (util/transform-malli-ast unparse-xf ast))

(ns oksa.unparse
  (:require [clojure.string :as str]
            [oksa.util :as util]))

(defn format-type
  [[type opts child]]
  (if (= :oksa/list type)
    (str "[" (format-type child) "]" (when (:oksa/non-null? opts) "!"))
    (str (name type) (when (:oksa/non-null? opts) "!"))))

(defn variable-name
  [variable]
  (str "$" (str/replace (name variable) #"^\$" "")))

(declare format-directives)

(defn format-variable-definition
  [variable opts type]
  (str (variable-name variable)
       ":"
       (format-type type)
       (when (:default opts)
         (str "=" (:default opts)))
       (when (:directives opts)
         (str " " (format-directives (:directives opts))))))

(defn format-variable-definitions
  [variable-definitions]
  (str "("
       (str/join ","
                 (map (fn [[variable opts type]]
                        (format-variable-definition variable opts type))
                      variable-definitions))
       ")"))

(declare format-arguments)

(defn format-directive
  [[directive-name opts :as _directive]]
  (str "@"
       (name directive-name)
       (when (:arguments opts) (format-arguments (:arguments opts)))))

(defn format-directives
  [directives]
  (str/join " " (map format-directive directives)))

(defmulti format-value class)
(defmethod format-value Number [x] (str x))
(defmethod format-value clojure.lang.Ratio [x] (str (double x)))
(defmethod format-value String [x] (str "\"" x "\"")) ; todo: think block strings through
(defmethod format-value Boolean [x] (str x))
(defmethod format-value nil [_] "null")
(defmethod format-value clojure.lang.Keyword [x]
  (let [enum-or-variable (name x)]
    (when-let [enum (re-matches #"^[^$].*$" enum-or-variable)]
      (assert (not (#{"true" "false" "null"} enum)) "invalid name"))
    enum-or-variable))
(defmethod format-value clojure.lang.PersistentVector
  [x]
  (str x))
(defmethod format-value clojure.lang.PersistentArrayMap
  [x]
  (str "{"
       (str/join ", " (map (fn [[object-field-name object-field-value]]
                             (str (name object-field-name) ":" (format-value object-field-value))) x))
       "}"))

(defn format-argument
  [[argument-name value]]
  (str (name argument-name) ":" (format-value value)))

(defn format-arguments
  [arguments]
  (str "(" (str/join ", " (map format-argument arguments)) ")"))

(def unparse-xf
  (letfn [(document
            [_opts & xs]
            (str/join (System/lineSeparator) xs))
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
     :<> document
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

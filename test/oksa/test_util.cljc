(ns oksa.test-util
  (:require [oksa.core :as core]))

#?(:bb nil
   :clj (import graphql.parser.Parser))

(defn unparse-and-validate
  ([x]
   (unparse-and-validate nil x))
  ([opts x]
   (let [graphql-query (core/unparse opts x)]
     #?(:bb nil
        :clj (Parser/parse graphql-query))
     graphql-query)))

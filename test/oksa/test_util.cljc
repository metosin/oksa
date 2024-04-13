(ns oksa.test-util
  (:require [oksa.core :as core])
  #?(:clj (:import [graphql.parser Parser])))

(defn unparse-and-validate
  [x]
  (let [graphql-query (core/unparse x)]
    #?(:clj (Parser/parse graphql-query))
    graphql-query))

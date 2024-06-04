(ns ^:generative oksa.generator-test
  (:require [#?(:clj  clojure.test
                :cljs cljs.test) :as t]
            [malli.generator :as mg]
            [malli.core :as m]
            [oksa.parse]
            [oksa.test-util :refer [unparse-and-validate]]))

#?(:bb nil
   :clj (require '[kaocha.config]
                 '[kaocha.testable]))

(def document-schema (oksa.parse/-graphql-dsl-lang {:oksa/strict true} :oksa.parse/Document))
(def document-parser (m/parser document-schema))

#?(:bb  nil
   :clj (t/deftest generator-test
          (let [seed (or (:kaocha.plugin.randomize/seed kaocha.testable/*config*)
                         (.nextLong (java.util.Random.)))
                samples (loop [seed seed
                               samples nil
                               tries 10]
                          (cond (some? samples) samples
                                (zero? tries) (throw (ex-info "could not generate examples" {}))
                                :else (recur
                                        (inc seed)
                                        (try (mg/generate document-schema {:size 10 :seed seed})
                                             (catch Exception _ nil))
                                        (dec tries))))]
            (prn ::seed seed)
            (doseq [sample samples]
              (prn ::x sample)
              (t/is (true? (or (= :malli.core/invalid (document-parser sample))
                               (string? (unparse-and-validate sample)))))))))
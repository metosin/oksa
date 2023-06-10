(ns app.core-test
  (:require [cljs.test :as t]
            [oksa.core :as core]))

(t/deftest app-test
  (t/testing "smoke test"
    (t/is (= "query {foo}" (core/unparse [:oksa/query {} [:foo]])))))

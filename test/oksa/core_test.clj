(ns oksa.core-test
  (:require [clojure.test :refer :all]
            [oksa.core :as core]))

(deftest unparse-test
  (testing "query"
    (is (= "query {foo}" (core/unparse [:query {} [:select [:foo]]])))
    (is (= "query {foo bar}" (core/unparse [:query {} [:select [:foo] [:bar]]])))
    (is (= "query {bar{qux{baz}}}" (core/unparse [:query {} [:select
                                                             [:bar
                                                              [:select
                                                               [:qux
                                                                [:select [:baz]]]]]]])))
    (is (= "query {foo bar{qux{baz}}}" (core/unparse [:query {} [:select
                                                                 [:foo]
                                                                 [:bar [:select
                                                                        [:qux [:select [:baz]]]]]]])))
    (is (= "query Foo {foo}" (core/unparse [:query {:name :Foo} [:select [:foo]]]))))
  (testing "mutation"
    (is (= "mutation {foo}" (core/unparse [:mutation {} [:select [:foo]]])))
    (is (= "mutation {foo bar}" (core/unparse [:mutation {} [:select [:foo] [:bar]]])))
    (is (= "mutation {bar{qux{baz}}}" (core/unparse [:mutation {} [:select [:bar [:select [:qux [:select [:baz]]]]]]])))
    (is (= "mutation {foo bar{qux{baz}}}" (core/unparse [:mutation {} [:select
                                                                       [:foo]
                                                                       [:bar [:select [:qux [:select [:baz]]]]]]])))
    (is (= "mutation Foo {foo}" (core/unparse [:mutation {:name :Foo} [:select [:foo]]]))))
  (testing "subscription"
    (is (= "subscription {foo}" (core/unparse [:subscription {} [:select [:foo]]])))
    (is (= "subscription {foo bar}" (core/unparse [:subscription {} [:select [:foo] [:bar]]])))
    (is (= "subscription {bar{qux{baz}}}" (core/unparse [:subscription {} [:select [:bar [:select [:qux [:select [:baz]]]]]]])))
    (is (= "subscription {foo bar{qux{baz}}}" (core/unparse [:subscription {} [:select [:foo] [:bar [:select [:qux [:select [:baz]]]]]]])))
    (is (= "subscription Foo {foo}" (core/unparse [:subscription {:name :Foo} [:select [:foo]]]))))
  (testing "selection set"
    (is (= "{foo}"
           (core/unparse [:select [:foo]])
           (core/unparse [[:foo]])))
    (is (= "{foo bar}"
           (core/unparse [:select [:foo] [:bar]])
           (core/unparse [[:foo] [:bar]])))
    (is (= "{bar{qux{baz}}}"
           (core/unparse [:select [:bar [:select [:qux [:select [:baz]]]]]])
           (core/unparse [[:bar [[:qux [[:baz]]]]]])))
    (is (= "{foo bar{qux{baz}}}"
           (core/unparse [:select [:foo] [:bar [:select [:qux [:select [:baz]]]]]])
           (core/unparse [[:foo] [:bar [[:qux [[:baz]]]]]])))
    (testing "support strings as field names"
      (is (= "{foo}"
             (core/unparse [["foo"]])
             (core/unparse [[:foo]])))))
  (testing "document"
    (is (= "{foo}"
           (core/unparse [:<> [:select [:foo]]])
           (core/unparse [:document [:select [:foo]]])))
    (is (= "{foo}\n{bar}"
           (core/unparse [:<> [:select [:foo]] [:select [:bar]]])
           (core/unparse [:document [:select [:foo]] [:select [:bar]]])))
    (is (= "{foo}\nquery {bar}\nmutation {qux}\nsubscription {baz}\nfragment foo on Foo{bar}"
           (core/unparse [:<>
                          [:select [:foo]]
                          [:query [:select [:bar]]]
                          [:mutation [:select [:qux]]]
                          [:subscription [:select [:baz]]]
                          [:fragment {:name :foo :on :Foo} [:select [:bar]]]])
           (core/unparse [:document
                          [:select [:foo]]
                          [:query [:select [:bar]]]
                          [:mutation [:select [:qux]]]
                          [:subscription [:select [:baz]]]
                          [:fragment {:name :foo :on :Foo} [:select [:bar]]]]))))
  (testing "fragment"
    (is (= "fragment Foo on Bar{foo}"
           (core/unparse [:# {:name :Foo :on :Bar} [:select [:foo]]])
           (core/unparse [:fragment {:name :Foo :on :Bar} [:select [:foo]]])))
    (is (= "fragment Foo on Bar{foo bar}"
           (core/unparse [:# {:name :Foo :on :Bar} [:select [:foo] [:bar]]])
           (core/unparse [:fragment {:name :Foo :on :Bar} [:select [:foo] [:bar]]])))
    (is (= "fragment Foo on Bar{bar{qux{baz}}}"
           (core/unparse [:# {:name :Foo :on :Bar} [:select [:bar [:select [:qux [:select [:baz]]]]]]])
           (core/unparse [:fragment {:name :Foo :on :Bar} [:select [:bar [:select [:qux [:select [:baz]]]]]]])))
    (is (= "fragment Foo on Bar{foo bar{qux{baz}}}"
           (core/unparse [:# {:name :Foo :on :Bar} [:select [:foo] [:bar [:select [:qux [:select [:baz]]]]]]])
           (core/unparse [:fragment {:name :Foo :on :Bar} [:select [:foo] [:bar [:select [:qux [:select [:baz]]]]]]]))))
  (testing "fragment spread"
    (is (= "{foo ...bar}"
           (core/unparse [:select
                          [:foo]
                          [:... {:name :bar}]])
           (core/unparse [:select
                          [:foo]
                          [:fragment-spread {:name :bar}]]))))
  (testing "inline fragment"
    (is (= "{foo ...{bar}}"
           (core/unparse [:select
                          [:foo]
                          [:... [:select [:bar]]]])
           (core/unparse [:select
                          [:foo]
                          [:inline-fragment [:select [:bar]]]])))
    (is (= "{foo ...on Bar{bar}}"
           (core/unparse [:select
                          [:foo]
                          [:... {:on :Bar} [:select [:bar]]]])
           (core/unparse [:select
                          [:foo]
                          [:inline-fragment {:on :Bar} [:select [:bar]]]]))))
  (testing "variable definitions"
    (testing "named type"
      (is (= "query ($fooVar:FooType){fooField}"
             (core/unparse [:query {:variable-definitions {:fooVar [:FooType]}}
                            [:select [:fooField]]]))))
    (testing "non-null named type")
    (is (= "query ($fooVar:FooType!){fooField}"
           (core/unparse [:query {:variable-definitions
                                  {:fooVar [:FooType {:oksa/non-null? true}]}}
                          [:select [:fooField]]])))
    (testing "named type within list"
      (is (= "query ($fooVar:[FooType]){fooField}"
             (core/unparse [:query {:variable-definitions
                                    {:fooVar [:oksa/list [:FooType]]}}
                            [:select [:fooField]]]))))
    (testing "non-null named type within list"
      (is (= "query ($fooVar:[FooType!]){fooField}"
             (core/unparse [:query {:variable-definitions
                                    {:fooVar [:oksa/list
                                              [:FooType {:oksa/non-null? true}]]}}
                            [:select [:fooField]]]))))
    (testing "named type within list"
      (is (= "query ($fooVar:[BarType]!){fooField}"
             (core/unparse [:query {:variable-definitions
                                    {:fooVar [:oksa/list {:oksa/non-null? true}
                                              [:BarType]]}}
                            [:select [:fooField]]]))))
    (testing "non-null type within non-null list"
      (is (= "query ($fooVar:[BarType!]!){fooField}"
             (core/unparse [:query {:variable-definitions
                                    {:fooVar [:oksa/list {:oksa/non-null? true}
                                              [:BarType {:oksa/non-null? true}]]}}
                            [:select [:fooField]]]))))
    (testing "named type within list within list"
      (is (= "query ($fooVar:[[BarType]]){fooField}"
             (core/unparse [:query {:variable-definitions
                                    {:fooVar [:oksa/list
                                              [:oksa/list
                                               [:BarType]]]}}
                            [:select [:fooField]]]))))
    (testing "non-null list within non-null list"
      (is (= "query ($fooVar:[[BarType]!]!){fooField}"
             (core/unparse [:query {:variable-definitions
                                    {:fooVar [:oksa/list {:oksa/non-null? true}
                                              [:oksa/list {:oksa/non-null? true}
                                               [:BarType]]]}}
                            [:select [:fooField]]]))))
    (testing "multiple variable definitions"
      (is (= "query ($fooVar:FooType,$barVar:BarType){fooField}"
             (core/unparse [:query {:variable-definitions
                                    {:fooVar [:FooType]
                                     :barVar [:BarType]}}
                            [:select [:fooField]]])))))
  (testing "variable names"
    (doseq [variable-name [:fooVar :$fooVar "fooVar" "$fooVar"]]
      (is (= "query ($fooVar:FooType){fooField}"
             (core/unparse [:query {:variable-definitions {variable-name [:FooType]}}
                            [[:fooField]]])))))
  (testing "aliases"
    (is (= "{bar:foo}"
           (core/unparse [[:foo {:alias "bar"}]])
           (core/unparse [[:foo {:alias :bar}]])))
    (is (= "{bar:foo baz:qux}"
           (core/unparse [[:foo {:alias "bar"}]
                          [:qux {:alias "baz"}]])
           (core/unparse [[:foo {:alias :bar}]
                          [:qux {:alias :baz}]])))
    (is (= "{bar:foo frob baz:qux}"
           (core/unparse [[:foo {:alias "bar"}]
                          [:frob]
                          [:qux {:alias "baz"}]])
           (core/unparse [[:foo {:alias :bar}]
                          [:frob]
                          [:qux {:alias :baz}]]))))
  (testing "arguments"
    (is (= "{foo}"
           (core/unparse [[:foo {:arguments {}}]])))
    (is (= "{foo(a:1, b:\"hello world\", c:true, d:null, e:foo, f:[1 2 3], g:{frob:{foo:1, bar:2}}, h:$fooVar)}"
           (core/unparse [[:foo {:arguments {:a 1
                                             :b "hello world"
                                             :c true
                                             :d nil
                                             :e :foo
                                             :f [1 2 3]
                                             :g {:frob {:foo 1
                                                        :bar 2}}
                                             :h :$fooVar}}]]))))
  (testing "directives"
    (is (= "query @foo{foo}"
           (core/unparse [:query {:directives [[:foo]]} [[:foo]]])))
    (is (= "query @foo @bar{foo}"
           (core/unparse [:query {:directives [[:foo] [:bar]]} [[:foo]]])))
    (is (= "query @foo(bar:123){foo}"
           (core/unparse [:query {:directives [[:foo {:arguments {:bar 123}}]]} [[:foo]]])))
    (is (= "{foo@foo(bar:123)}"
           (core/unparse [[:foo {:directives [[:foo {:arguments {:bar 123}}]]}]])))
    (is (= "{...foo@foo(bar:123)}"
           (core/unparse [[:fragment-spread {:name :foo
                                             :directives [[:foo {:arguments {:bar 123}}]]}]])))
    (is (= "{...@foo(bar:123){bar}}"
           (core/unparse [[:inline-fragment {:directives [[:foo {:arguments {:bar 123}}]]}
                           [[:bar]]]])))
    (is (= "fragment foo on Foo@foo(bar:123){bar}"
           (core/unparse [:fragment {:name :foo
                                     :on :Foo
                                     :directives [[:foo {:arguments {:bar 123}}]]}
                          [[:bar]]])))))

(ns oksa.core-test
  (:require [clojure.test :refer :all]
            [oksa.core :as core]))

(deftest unparse-test
  (testing "query"
    (is (= "query {foo}" (core/unparse [:query {} [:foo]])))
    (is (= "query {foo bar}" (core/unparse [:query {} [:foo :bar]])))
    (is (= "query {bar{qux{baz}}}" (core/unparse [:query {} [:bar [:qux [:baz]]]])))
    (is (= "query {foo bar{qux{baz}}}" (core/unparse [:query {} [:foo :bar [:qux [:baz]]]])))
    (is (= "query Foo {foo}" (core/unparse [:query {:name :Foo} [:foo]]))))
  (testing "mutation"
    (is (= "mutation {foo}" (core/unparse [:mutation {} [:foo]])))
    (is (= "mutation {foo bar}" (core/unparse [:mutation {} [:foo :bar]])))
    (is (= "mutation {bar{qux{baz}}}" (core/unparse [:mutation {} [:bar [:qux [:baz]]]])))
    (is (= "mutation {foo bar{qux{baz}}}" (core/unparse [:mutation {} [:foo :bar [:qux [:baz]]]])))
    (is (= "mutation Foo {foo}" (core/unparse [:mutation {:name :Foo} [:foo]]))))
  (testing "subscription"
    (is (= "subscription {foo}" (core/unparse [:subscription {} [:foo]])))
    (is (= "subscription {foo bar}" (core/unparse [:subscription {} [:foo :bar]])))
    (is (= "subscription {bar{qux{baz}}}" (core/unparse [:subscription {} [:bar [:qux [:baz]]]])))
    (is (= "subscription {foo bar{qux{baz}}}" (core/unparse [:subscription {} [:foo :bar [:qux [:baz]]]])))
    (is (= "subscription Foo {foo}" (core/unparse [:subscription {:name :Foo} [:foo]]))))
  (testing "selection set"
    (is (= "{foo}"
           (core/unparse [:foo])))
    (is (= "{foo bar}"
           (core/unparse [:foo :bar])))
    (is (= "{bar{qux{baz}}}"
           (core/unparse [:bar [:qux [:baz]]])))
    (is (= "{foo bar{qux{baz}}}"
           (core/unparse [:foo :bar [:qux [:baz]]])))
    (is (= "{foo bar{qux baz}}"
           (core/unparse [:foo :bar [:qux :baz]])))
    (is (= "{foo{bar{baz qux} frob}}"
           (core/unparse [:foo [:bar [:baz :qux] :frob]])))
    (testing "support strings as field names"
      (is (= "{foo}"
             (core/unparse ["foo"])
             (core/unparse [:foo]))))
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
                                               :h :$fooVar}}]])))))
  (testing "document"
    (is (= "{foo}"
           (core/unparse [:<> [:foo]])
           (core/unparse [:document [:foo]])))
    (is (= "{foo}\n{bar}"
           (core/unparse [:<> [:foo] [:bar]])
           (core/unparse [:document [:foo] [:bar]])))
    (is (= "{foo}\nquery {bar}\nmutation {qux}\nsubscription {baz}\nfragment foo on Foo{bar}"
           (core/unparse [:<>
                          [:foo]
                          [:query [:bar]]
                          [:mutation [:qux]]
                          [:subscription [:baz]]
                          [:fragment {:name :foo :on :Foo} [:bar]]])
           (core/unparse [:document
                          [:foo]
                          [:query [:bar]]
                          [:mutation [:qux]]
                          [:subscription [:baz]]
                          [:fragment {:name :foo :on :Foo} [:bar]]]))))
  (testing "fragment"
    (is (= "fragment Foo on Bar{foo}"
           (core/unparse [:# {:name :Foo :on :Bar} [:foo]])
           (core/unparse [:fragment {:name :Foo :on :Bar} [:foo]])))
    (is (= "fragment Foo on Bar{foo bar}"
           (core/unparse [:# {:name :Foo :on :Bar} [:foo :bar]])
           (core/unparse [:fragment {:name :Foo :on :Bar} [:foo :bar]])))
    (is (= "fragment Foo on Bar{bar{qux{baz}}}"
           (core/unparse [:# {:name :Foo :on :Bar} [:bar [:qux [:baz]]]])
           (core/unparse [:fragment {:name :Foo :on :Bar} [:bar [:qux [:baz]]]])))
    (is (= "fragment Foo on Bar{foo bar{qux{baz}}}"
           (core/unparse [:# {:name :Foo :on :Bar} [:foo :bar [:qux [:baz]]]])
           (core/unparse [:fragment {:name :Foo :on :Bar} [:foo :bar [:qux [:baz]]]]))))
  (testing "fragment spread"
    (is (= "{foo ...bar}"
           (core/unparse [:foo [:... {:name :bar}]])
           (core/unparse [:foo [:fragment-spread {:name :bar}]]))))
  (testing "inline fragment"
    (is (= "{foo ...{bar}}"
           (core/unparse [:foo [:... [:bar]]])
           (core/unparse [:foo [:inline-fragment [:bar]]])))
    (is (= "{foo ...on Bar{bar}}"
           (core/unparse [:foo [:... {:on :Bar} [:bar]]])
           (core/unparse [:foo [:inline-fragment {:on :Bar} [:bar]]]))))
  (testing "variable definitions"
    (testing "named type"
      (is (= "query ($fooVar:FooType){fooField}"
             (core/unparse [:query {:variables [:fooVar :FooType]}
                            [:fooField]]))))
    (testing "non-null named type")
    (is (= "query ($fooVar:FooType!){fooField}"
           (core/unparse [:query {:variables [:fooVar [:FooType {:oksa/non-null? true}]]}
                          [:fooField]])))
    (testing "named type within list"
      (is (= "query ($fooVar:[FooType]){fooField}"
             (core/unparse [:query {:variables [:fooVar [:oksa/list :FooType]]}
                            [:fooField]])
             (core/unparse [:query {:variables [:fooVar [:FooType]]}
                            [:fooField]]))))
    (testing "non-null named type within list"
      (is (= "query ($fooVar:[FooType!]){fooField}"
             (core/unparse [:query {:variables [:fooVar [:oksa/list
                                                         [:FooType {:oksa/non-null? true}]]]}
                            [:fooField]])
             (core/unparse [:query {:variables [:fooVar [:FooType!]]}
                            [:fooField]]))))
    (testing "named type within list"
      (is (= "query ($fooVar:[BarType]!){fooField}"
             (core/unparse [:query {:variables [:fooVar [:oksa/list {:oksa/non-null? true}
                                                         :BarType]]}
                            [:fooField]])
             (core/unparse [:query {:variables [:fooVar [:! :BarType]]}
                            [:fooField]]))))
    (testing "non-null type within non-null list"
      (is (= "query ($fooVar:[BarType!]!){fooField}"
             (core/unparse [:query {:variables [:fooVar [:oksa/list {:oksa/non-null? true}
                                                         [:BarType {:oksa/non-null? true}]]]}
                            [:fooField]])
             (core/unparse [:query {:variables [:fooVar [:! :BarType!]]}
                            [:fooField]]))))
    (testing "named type within list within list"
      (is (= "query ($fooVar:[[BarType]]){fooField}"
             (core/unparse [:query {:variables [:fooVar [:oksa/list
                                                         [:oksa/list
                                                          :BarType]]]}
                            [:fooField]])
             (core/unparse [:query {:variables [:fooVar [[:BarType]]]}
                            [:fooField]]))))
    (testing "non-null list within non-null list"
      (is (= "query ($fooVar:[[BarType]!]!){fooField}"
             (core/unparse [:query {:variables [:fooVar [:oksa/list {:oksa/non-null? true}
                                                         [:oksa/list {:oksa/non-null? true}
                                                          :BarType]]]}
                            [:fooField]])
             (core/unparse [:query {:variables [:fooVar [:! [:! :BarType]]]}
                            [:fooField]]))))
    (testing "multiple variable definitions"
      (is (= "query ($fooVar:FooType,$barVar:BarType){fooField}"
             (core/unparse [:query {:variables [:fooVar :FooType
                                                :barVar :BarType]}
                            [:fooField]]))))
    (testing "default values"
      (is (= "query ($fooVar:Foo=123){fooField(foo:$fooVar)}"
             (core/unparse [:query {:variables [:$fooVar {:default 123} :Foo]}
                            [[:fooField {:arguments {:foo :$fooVar}}]]])))))
  (testing "variable names"
    (doseq [variable-name [:fooVar :$fooVar "fooVar" "$fooVar"]]
      (is (= "query ($fooVar:FooType){fooField}"
             (core/unparse [:query {:variables [variable-name :FooType]}
                            [:fooField]])))))
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
                          :frob
                          [:qux {:alias "baz"}]])
           (core/unparse [[:foo {:alias :bar}]
                          :frob
                          [:qux {:alias :baz}]]))))
  (testing "directives"
    (is (= "query @foo{foo}"
           (core/unparse [:query {:directives [:foo]} [:foo]])
           (core/unparse [:query {:directives [[:foo]]} [:foo]])))
    (is (= "query @foo @bar{foo}"
           (core/unparse [:query {:directives [:foo :bar]} [:foo]])
           (core/unparse [:query {:directives [[:foo] [:bar]]} [:foo]])))
    (is (= "query @foo(bar:123){foo}"
           (core/unparse [:query {:directives [[:foo {:arguments {:bar 123}}]]} [:foo]])))
    (is (= "{foo@bar}"
           (core/unparse [[:foo {:directives [:bar]}]])))
    (is (= "{foo@bar qux@baz}"
           (core/unparse [[:foo {:directives [:bar]}]
                          [:qux {:directives [:baz]}]])))
    (is (= "{foo@foo(bar:123)}"
           (core/unparse [[:foo {:directives [[:foo {:arguments {:bar 123}}]]}]])))
    (is (= "{...foo@foo(bar:123)}"
           (core/unparse [[:fragment-spread {:name :foo
                                             :directives [[:foo {:arguments {:bar 123}}]]}]])))
    (is (= "{...@foo(bar:123){bar}}"
           (core/unparse [[:inline-fragment {:directives [[:foo {:arguments {:bar 123}}]]}
                           [:bar]]])))
    (is (= "fragment foo on Foo@foo(bar:123){bar}"
           (core/unparse [:fragment {:name :foo
                                     :on :Foo
                                     :directives [[:foo {:arguments {:bar 123}}]]}
                          [:bar]])))
    (is (= "query ($foo:Bar @fooDirective){fooField}"
           (core/unparse [:query {:variables [:foo {:directives [:fooDirective]} :Bar]}
                          [:fooField]])))
    (is (= "query ($foo:Bar @fooDirective @barDirective){fooField}"
           (core/unparse [:query {:variables [:foo {:directives [:fooDirective :barDirective]} :Bar]}
                          [:fooField]])))
    (is (= "query ($foo:Bar @fooDirective(fooArg:123)){fooField}"
           (core/unparse [:query {:variables [:foo {:directives [[:fooDirective {:arguments {:fooArg 123}}]]} :Bar]}
                          [:fooField]])))))

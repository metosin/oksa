(ns oksa.core-test
  (:require [#?(:clj clojure.test
                :cljs cljs.test) :as t]
            [oksa.core :as core]))

(t/deftest unparse-test
  (t/testing "query"
    (t/is (= "query {foo}" (core/unparse [:query {} [:foo]])))
    (t/is (= "query {foo bar}" (core/unparse [:query {} [:foo :bar]])))
    (t/is (= "query {bar{qux{baz}}}" (core/unparse [:query {} [:bar [:qux [:baz]]]])))
    (t/is (= "query {foo bar{qux{baz}}}" (core/unparse [:query {} [:foo :bar [:qux [:baz]]]])))
    (t/is (= "query Foo {foo}" (core/unparse [:query {:name :Foo} [:foo]]))))
  (t/testing "mutation"
    (t/is (= "mutation {foo}" (core/unparse [:mutation {} [:foo]])))
    (t/is (= "mutation {foo bar}" (core/unparse [:mutation {} [:foo :bar]])))
    (t/is (= "mutation {bar{qux{baz}}}" (core/unparse [:mutation {} [:bar [:qux [:baz]]]])))
    (t/is (= "mutation {foo bar{qux{baz}}}" (core/unparse [:mutation {} [:foo :bar [:qux [:baz]]]])))
    (t/is (= "mutation Foo {foo}" (core/unparse [:mutation {:name :Foo} [:foo]]))))
  (t/testing "subscription"
    (t/is (= "subscription {foo}" (core/unparse [:subscription {} [:foo]])))
    (t/is (= "subscription {foo bar}" (core/unparse [:subscription {} [:foo :bar]])))
    (t/is (= "subscription {bar{qux{baz}}}" (core/unparse [:subscription {} [:bar [:qux [:baz]]]])))
    (t/is (= "subscription {foo bar{qux{baz}}}" (core/unparse [:subscription {} [:foo :bar [:qux [:baz]]]])))
    (t/is (= "subscription Foo {foo}" (core/unparse [:subscription {:name :Foo} [:foo]]))))
  (t/testing "selection set"
    (t/is (= "{foo}"
           (core/unparse [:foo])))
    (t/is (= "{foo bar}"
           (core/unparse [:foo :bar])))
    (t/is (= "{bar{qux{baz}}}"
           (core/unparse [:bar [:qux [:baz]]])))
    (t/is (= "{foo bar{qux{baz}}}"
           (core/unparse [:foo :bar [:qux [:baz]]])))
    (t/is (= "{foo bar{qux baz}}"
           (core/unparse [:foo :bar [:qux :baz]])))
    (t/is (= "{foo{bar{baz qux} frob}}"
           (core/unparse [:foo [:bar [:baz :qux] :frob]])))
    (t/testing "support strings as field names"
      (t/is (= "{foo}"
             (core/unparse ["foo"])
             (core/unparse [:foo]))))
    (t/testing "arguments"
      (t/is (= "{foo}"
             (core/unparse [[:foo {:arguments {}}]])))
      (t/is (= "{foo(a:1, b:\"hello world\", c:true, d:null, e:foo, f:[1 2 3], g:{frob:{foo:1, bar:2}}, h:$fooVar)}"
             (core/unparse [[:foo {:arguments {:a 1
                                               :b "hello world"
                                               :c true
                                               :d nil
                                               :e :foo
                                               :f [1 2 3]
                                               :g {:frob {:foo 1
                                                          :bar 2}}
                                               :h :$fooVar}}]])))
      (t/testing "escaping special characters"
        (t/is (= "{fooField(foo:\"\\\"\")}"
               (core/unparse [[:fooField {:arguments {:foo "\""}}]])))
        (t/is (= "{fooField(foo:\"\\\\\")}"
               (core/unparse [[:fooField {:arguments {:foo "\\"}}]])))
        (t/is (= "{fooField(foo:\"foo\\b\\f\\r\\n\\tbar\")}"
               (core/unparse [[:fooField {:arguments {:foo (str "foo\b\f\r\n\tbar")}}]]))))))
  (t/testing "document"
    (t/is (= "{foo}"
           (core/unparse [:<> [:foo]])
           (core/unparse [:document [:foo]])))
    (t/is (= "{foo}\n{bar}"
           (core/unparse [:<> [:foo] [:bar]])
           (core/unparse [:document [:foo] [:bar]])))
    (t/is (= "{foo}\nquery {bar}\nmutation {qux}\nsubscription {baz}\nfragment foo on Foo{bar}"
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
  (t/testing "fragment"
    (t/is (= "fragment Foo on Bar{foo}"
           (core/unparse [:# {:name :Foo :on :Bar} [:foo]])
           (core/unparse [:fragment {:name :Foo :on :Bar} [:foo]])))
    (t/is (= "fragment Foo on Bar{foo bar}"
           (core/unparse [:# {:name :Foo :on :Bar} [:foo :bar]])
           (core/unparse [:fragment {:name :Foo :on :Bar} [:foo :bar]])))
    (t/is (= "fragment Foo on Bar{bar{qux{baz}}}"
           (core/unparse [:# {:name :Foo :on :Bar} [:bar [:qux [:baz]]]])
           (core/unparse [:fragment {:name :Foo :on :Bar} [:bar [:qux [:baz]]]])))
    (t/is (= "fragment Foo on Bar{foo bar{qux{baz}}}"
           (core/unparse [:# {:name :Foo :on :Bar} [:foo :bar [:qux [:baz]]]])
           (core/unparse [:fragment {:name :Foo :on :Bar} [:foo :bar [:qux [:baz]]]]))))
  (t/testing "fragment spread"
    (t/is (= "{foo ...bar}"
           (core/unparse [:foo [:... {:name :bar}]])
           (core/unparse [:foo [:fragment-spread {:name :bar}]]))))
  (t/testing "inline fragment"
    (t/is (= "{foo ...{bar}}"
           (core/unparse [:foo [:... [:bar]]])
           (core/unparse [:foo [:inline-fragment [:bar]]])))
    (t/is (= "{foo ...on Bar{bar}}"
           (core/unparse [:foo [:... {:on :Bar} [:bar]]])
           (core/unparse [:foo [:inline-fragment {:on :Bar} [:bar]]]))))
  (t/testing "variable definitions"
    (t/testing "named type"
      (t/is (= "query ($fooVar:FooType){fooField}"
             (core/unparse [:query {:variables [:fooVar :FooType]}
                            [:fooField]]))))
    (t/testing "non-null named type")
    (t/is (= "query ($fooVar:FooType!){fooField}"
           (core/unparse [:query {:variables [:fooVar [:FooType {:oksa/non-null? true}]]}
                          [:fooField]])))
    (t/testing "named type within list"
      (t/is (= "query ($fooVar:[FooType]){fooField}"
             (core/unparse [:query {:variables [:fooVar [:oksa/list :FooType]]}
                            [:fooField]])
             (core/unparse [:query {:variables [:fooVar [:FooType]]}
                            [:fooField]]))))
    (t/testing "non-null named type within list"
      (t/is (= "query ($fooVar:[FooType!]){fooField}"
             (core/unparse [:query {:variables [:fooVar [:oksa/list
                                                         [:FooType {:oksa/non-null? true}]]]}
                            [:fooField]])
             (core/unparse [:query {:variables [:fooVar [:FooType!]]}
                            [:fooField]]))))
    (t/testing "named type within list"
      (t/is (= "query ($fooVar:[BarType]!){fooField}"
             (core/unparse [:query {:variables [:fooVar [:oksa/list {:oksa/non-null? true}
                                                         :BarType]]}
                            [:fooField]])
             (core/unparse [:query {:variables [:fooVar [:! :BarType]]}
                            [:fooField]]))))
    (t/testing "non-null type within non-null list"
      (t/is (= "query ($fooVar:[BarType!]!){fooField}"
             (core/unparse [:query {:variables [:fooVar [:oksa/list {:oksa/non-null? true}
                                                         [:BarType {:oksa/non-null? true}]]]}
                            [:fooField]])
             (core/unparse [:query {:variables [:fooVar [:! :BarType!]]}
                            [:fooField]]))))
    (t/testing "named type within list within list"
      (t/is (= "query ($fooVar:[[BarType]]){fooField}"
             (core/unparse [:query {:variables [:fooVar [:oksa/list
                                                         [:oksa/list
                                                          :BarType]]]}
                            [:fooField]])
             (core/unparse [:query {:variables [:fooVar [[:BarType]]]}
                            [:fooField]]))))
    (t/testing "non-null list within non-null list"
      (t/is (= "query ($fooVar:[[BarType]!]!){fooField}"
             (core/unparse [:query {:variables [:fooVar [:oksa/list {:oksa/non-null? true}
                                                         [:oksa/list {:oksa/non-null? true}
                                                          :BarType]]]}
                            [:fooField]])
             (core/unparse [:query {:variables [:fooVar [:! [:! :BarType]]]}
                            [:fooField]]))))
    (t/testing "multiple variable definitions"
      (t/is (= "query ($fooVar:FooType,$barVar:BarType){fooField}"
             (core/unparse [:query {:variables [:fooVar :FooType
                                                :barVar :BarType]}
                            [:fooField]]))))
    (t/testing "default values"
      (t/is (= "query ($fooVar:Foo=123){fooField(foo:$fooVar)}"
             (core/unparse [:query {:variables [:$fooVar {:default 123} :Foo]}
                            [[:fooField {:arguments {:foo :$fooVar}}]]])))))
  (t/testing "variable names"
    (doseq [variable-name [:fooVar :$fooVar "fooVar" "$fooVar"]]
      (t/is (= "query ($fooVar:FooType){fooField}"
             (core/unparse [:query {:variables [variable-name :FooType]}
                            [:fooField]])))))
  (t/testing "aliases"
    (t/is (= "{bar:foo}"
           (core/unparse [[:foo {:alias "bar"}]])
           (core/unparse [[:foo {:alias :bar}]])))
    (t/is (= "{bar:foo baz:qux}"
           (core/unparse [[:foo {:alias "bar"}]
                          [:qux {:alias "baz"}]])
           (core/unparse [[:foo {:alias :bar}]
                          [:qux {:alias :baz}]])))
    (t/is (= "{bar:foo frob baz:qux}"
           (core/unparse [[:foo {:alias "bar"}]
                          :frob
                          [:qux {:alias "baz"}]])
           (core/unparse [[:foo {:alias :bar}]
                          :frob
                          [:qux {:alias :baz}]]))))
  (t/testing "directives"
    (t/is (= "query @foo{foo}"
           (core/unparse [:query {:directives [:foo]} [:foo]])
           (core/unparse [:query {:directives [[:foo]]} [:foo]])))
    (t/is (= "query @foo @bar{foo}"
           (core/unparse [:query {:directives [:foo :bar]} [:foo]])
           (core/unparse [:query {:directives [[:foo] [:bar]]} [:foo]])))
    (t/is (= "query @foo(bar:123){foo}"
           (core/unparse [:query {:directives [[:foo {:arguments {:bar 123}}]]} [:foo]])))
    (t/is (= "{foo@bar}"
           (core/unparse [[:foo {:directives [:bar]}]])))
    (t/is (= "{foo@bar qux@baz}"
           (core/unparse [[:foo {:directives [:bar]}]
                          [:qux {:directives [:baz]}]])))
    (t/is (= "{foo@foo(bar:123)}"
           (core/unparse [[:foo {:directives [[:foo {:arguments {:bar 123}}]]}]])))
    (t/is (= "{...foo@fooDirective @barDirective}"
           (core/unparse [[:fragment-spread {:name :foo
                                             :directives [:fooDirective :barDirective]}]])))
    (t/is (= "{...foo@foo(bar:123)}"
           (core/unparse [[:fragment-spread {:name :foo
                                             :directives [[:foo {:arguments {:bar 123}}]]}]])))
    (t/is (= "{...@fooDirective @barDirective{bar}}"
           (core/unparse [[:inline-fragment {:directives [:fooDirective :barDirective]}
                           [:bar]]])))
    (t/is (= "{...@foo(bar:123){bar}}"
           (core/unparse [[:inline-fragment {:directives [[:foo {:arguments {:bar 123}}]]}
                           [:bar]]])))
    (t/is (= "fragment foo on Foo@foo(bar:123){bar}"
           (core/unparse [:fragment {:name :foo
                                     :on :Foo
                                     :directives [[:foo {:arguments {:bar 123}}]]}
                          [:bar]])))
    (t/is (= "fragment foo on Foo@fooDirective @barDirective{bar}"
           (core/unparse [:fragment {:name :foo
                                     :on :Foo
                                     :directives [:fooDirective :barDirective]}
                          [:bar]])))
    (t/is (= "query ($foo:Bar @fooDirective){fooField}"
           (core/unparse [:query {:variables [:foo {:directives [:fooDirective]} :Bar]}
                          [:fooField]])))
    (t/is (= "query ($foo:Bar @fooDirective @barDirective){fooField}"
           (core/unparse [:query {:variables [:foo {:directives [:fooDirective :barDirective]} :Bar]}
                          [:fooField]])))
    (t/is (= "query ($foo:Bar @fooDirective(fooArg:123)){fooField}"
           (core/unparse [:query {:variables [:foo {:directives [[:fooDirective {:arguments {:fooArg 123}}]]} :Bar]}
                          [:fooField]])))))

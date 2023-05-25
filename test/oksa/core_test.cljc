(ns oksa.core-test
  (:require [#?(:clj clojure.test
                :cljs cljs.test) :as t]
            [oksa.core :as core]))

(t/deftest unparse-test
  (t/testing "query"
    (t/is (= "query {foo}" (core/unparse [:oksa/query {} [:foo]])))
    (t/is (= "query {foo bar}" (core/unparse [:oksa/query {} [:foo :bar]])))
    (t/is (= "query {bar{qux{baz}}}" (core/unparse [:oksa/query {} [:bar [:qux [:baz]]]])))
    (t/is (= "query {foo bar{qux{baz}}}" (core/unparse [:oksa/query {} [:foo :bar [:qux [:baz]]]])))
    (t/is (= "query Foo {foo}" (core/unparse [:oksa/query {:name :Foo} [:foo]])))
    (t/testing "non-ambiguity"
      (t/is (thrown? #?(:clj Exception :cljs js/Error) (core/unparse [:oksa/query [:oksa/query [:baz]]])))
      (t/is (= "query {query{baz}}" (core/unparse [:oksa/query [:query [:baz]]])))
      (t/is (= "{query{query{baz}}}" (core/unparse [:query [:query [:baz]]])))))
  (t/testing "mutation"
    (t/is (= "mutation {foo}" (core/unparse [:oksa/mutation {} [:foo]])))
    (t/is (= "mutation {foo bar}" (core/unparse [:oksa/mutation {} [:foo :bar]])))
    (t/is (= "mutation {bar{qux{baz}}}" (core/unparse [:oksa/mutation {} [:bar [:qux [:baz]]]])))
    (t/is (= "mutation {foo bar{qux{baz}}}" (core/unparse [:oksa/mutation {} [:foo :bar [:qux [:baz]]]])))
    (t/is (= "mutation Foo {foo}" (core/unparse [:oksa/mutation {:name :Foo} [:foo]])))
    (t/testing "non-ambiguity"
      (t/is (thrown? #?(:clj Exception :cljs js/Error) (core/unparse [:oksa/mutation [:oksa/mutation [:baz]]])))
      (t/is (= "mutation {mutation{baz}}" (core/unparse [:oksa/mutation [:mutation [:baz]]])))
      (t/is (= "{mutation{mutation{baz}}}" (core/unparse [:mutation [:mutation [:baz]]])))))
  (t/testing "subscription"
    (t/is (= "subscription {foo}" (core/unparse [:oksa/subscription {} [:foo]])))
    (t/is (= "subscription {foo bar}" (core/unparse [:oksa/subscription {} [:foo :bar]])))
    (t/is (= "subscription {bar{qux{baz}}}" (core/unparse [:oksa/subscription {} [:bar [:qux [:baz]]]])))
    (t/is (= "subscription {foo bar{qux{baz}}}" (core/unparse [:oksa/subscription {} [:foo :bar [:qux [:baz]]]])))
    (t/is (= "subscription Foo {foo}" (core/unparse [:oksa/subscription {:name :Foo} [:foo]])))
    (t/testing "non-ambiguity"
      (t/is (thrown? #?(:clj Exception :cljs js/Error) (core/unparse [:oksa/subscription [:oksa/subscription [:baz]]])))
      (t/is (= "subscription {subscription{baz}}" (core/unparse [:oksa/subscription [:subscription [:baz]]])))
      (t/is (= "{subscription{subscription{baz}}}" (core/unparse [:subscription [:subscription [:baz]]])))))
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
      #?(:clj (t/is (= "{foo(a:0.3333333333333333)}"
                       (core/unparse [[:foo {:arguments {:a 1/3}}]]))))
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
             (core/unparse [:oksa/document [:foo]])))
    (t/is (= "{foo}\n{bar}"
             (core/unparse [:<> [:foo] [:bar]])
             (core/unparse [:oksa/document [:foo] [:bar]])))
    (t/is (= "{foo}\nquery {bar}\nmutation {qux}\nsubscription {baz}\nfragment foo on Foo{bar}"
             (core/unparse [:<>
                            [:foo]
                            [:oksa/query [:bar]]
                            [:oksa/mutation [:qux]]
                            [:oksa/subscription [:baz]]
                            [:oksa/fragment {:name :foo :on :Foo} [:bar]]])
             (core/unparse [:oksa/document
                            [:foo]
                            [:oksa/query [:bar]]
                            [:oksa/mutation [:qux]]
                            [:oksa/subscription [:baz]]
                            [:oksa/fragment {:name :foo :on :Foo} [:bar]]])))
    (t/testing "non-ambiguity"
      (t/is (thrown? #?(:clj Exception :cljs js/Error) (core/unparse [:oksa/document [:oksa/document [:baz]]])))
      (t/is (= "{document{baz}}" (core/unparse [:oksa/document [:document [:baz]]])))
      (t/is (= "{document{document{baz}}}" (core/unparse [:document [:document [:baz]]])))))
  (t/testing "fragment"
    (t/is (= "fragment Foo on Bar{foo}"
             (core/unparse [:# {:name :Foo :on :Bar} [:foo]])
             (core/unparse [:oksa/fragment {:name :Foo :on :Bar} [:foo]])))
    (t/is (= "fragment Foo on Bar{foo bar}"
             (core/unparse [:# {:name :Foo :on :Bar} [:foo :bar]])
             (core/unparse [:oksa/fragment {:name :Foo :on :Bar} [:foo :bar]])))
    (t/is (= "fragment Foo on Bar{bar{qux{baz}}}"
             (core/unparse [:# {:name :Foo :on :Bar} [:bar [:qux [:baz]]]])
             (core/unparse [:oksa/fragment {:name :Foo :on :Bar} [:bar [:qux [:baz]]]])))
    (t/is (= "fragment Foo on Bar{foo bar{qux{baz}}}"
             (core/unparse [:# {:name :Foo :on :Bar} [:foo :bar [:qux [:baz]]]])
             (core/unparse [:oksa/fragment {:name :Foo :on :Bar} [:foo :bar [:qux [:baz]]]])))
    (t/testing "non-ambiguity"
      (t/is (thrown? #?(:clj Exception :cljs js/Error) (core/unparse [:oksa/fragment {:name :Foo :on :Bar} [:oksa/fragment {:name :Foo :on :Bar} [:baz]]])))
      (t/is (= "fragment Foo on Bar{fragment{baz}}" (core/unparse [:oksa/fragment {:name :Foo :on :Bar} [:fragment [:baz]]])))
      (t/is (= "{fragment{fragment{baz}}}" (core/unparse [:fragment [:fragment [:baz]]])))))
  (t/testing "fragment spread"
    (t/is (= "{foo ...bar}"
             (core/unparse [:foo [:... {:name :bar}]])
             (core/unparse [:foo [:oksa/fragment-spread {:name :bar}]]))))
  (t/testing "inline fragment"
    (t/is (= "{foo ...{bar}}"
             (core/unparse [:foo [:... [:bar]]])
             (core/unparse [:foo [:oksa/inline-fragment [:bar]]])))
    (t/is (= "{foo ...on Bar{bar}}"
             (core/unparse [:foo [:... {:on :Bar} [:bar]]])
             (core/unparse [:foo [:oksa/inline-fragment {:on :Bar} [:bar]]]))))
  (t/testing "variable definitions"
    (t/testing "named type"
      (t/is (= "query ($fooVar:FooType){fooField}"
               (core/unparse [:oksa/query {:variables [:fooVar :FooType]}
                              [:fooField]]))))
    (t/testing "non-null named type")
    (t/is (= "query ($fooVar:FooType!){fooField}"
             (core/unparse [:oksa/query {:variables [:fooVar [:FooType {:non-null true}]]}
                            [:fooField]])))
    (t/testing "named type within list"
      (t/is (= "query ($fooVar:[FooType]){fooField}"
               (core/unparse [:oksa/query {:variables [:fooVar [:oksa/list :FooType]]}
                              [:fooField]])
               (core/unparse [:oksa/query {:variables [:fooVar [:FooType]]}
                              [:fooField]]))))
    (t/testing "non-null named type within list"
      (t/is (= "query ($fooVar:[FooType!]){fooField}"
               (core/unparse [:oksa/query {:variables [:fooVar [:oksa/list
                                                                [:FooType {:non-null true}]]]}
                              [:fooField]])
               (core/unparse [:oksa/query {:variables [:fooVar [:FooType!]]}
                              [:fooField]]))))
    (t/testing "named type within list"
      (t/is (= "query ($fooVar:[BarType]!){fooField}"
               (core/unparse [:oksa/query {:variables [:fooVar [:oksa/list {:non-null true}
                                                                :BarType]]}
                              [:fooField]])
               (core/unparse [:oksa/query {:variables [:fooVar [:! :BarType]]}
                              [:fooField]]))))
    (t/testing "non-null type within non-null list"
      (t/is (= "query ($fooVar:[BarType!]!){fooField}"
               (core/unparse [:oksa/query {:variables [:fooVar [:oksa/list {:non-null true}
                                                                [:BarType {:non-null true}]]]}
                              [:fooField]])
               (core/unparse [:oksa/query {:variables [:fooVar [:! :BarType!]]}
                              [:fooField]]))))
    (t/testing "named type within list within list"
      (t/is (= "query ($fooVar:[[BarType]]){fooField}"
               (core/unparse [:oksa/query {:variables [:fooVar [:oksa/list
                                                                [:oksa/list
                                                                 :BarType]]]}
                              [:fooField]])
               (core/unparse [:oksa/query {:variables [:fooVar [[:BarType]]]}
                              [:fooField]]))))
    (t/testing "non-null list within non-null list"
      (t/is (= "query ($fooVar:[[BarType]!]!){fooField}"
               (core/unparse [:oksa/query {:variables [:fooVar [:oksa/list {:non-null true}
                                                                [:oksa/list {:non-null true}
                                                                 :BarType]]]}
                              [:fooField]])
               (core/unparse [:oksa/query {:variables [:fooVar [:! [:! :BarType]]]}
                              [:fooField]]))))
    (t/testing "multiple variable definitions"
      (t/is (= "query ($fooVar:FooType,$barVar:BarType){fooField}"
               (core/unparse [:oksa/query {:variables [:fooVar :FooType
                                                       :barVar :BarType]}
                              [:fooField]]))))
    (t/testing "default values"
      (t/is (= "query ($fooVar:Foo=123){fooField(foo:$fooVar)}"
               (core/unparse [:oksa/query {:variables [:$fooVar {:default 123} :Foo]}
                              [[:fooField {:arguments {:foo :$fooVar}}]]])))))
  (t/testing "variable names"
    (doseq [variable-name [:fooVar :$fooVar "fooVar" "$fooVar"]]
      (t/is (= "query ($fooVar:FooType){fooField}"
               (core/unparse [:oksa/query {:variables [variable-name :FooType]}
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
             (core/unparse [:oksa/query {:directives [:foo]} [:foo]])
             (core/unparse [:oksa/query {:directives [[:foo]]} [:foo]])))
    (t/is (= "query @foo @bar{foo}"
             (core/unparse [:oksa/query {:directives [:foo :bar]} [:foo]])
             (core/unparse [:oksa/query {:directives [[:foo] [:bar]]} [:foo]])))
    (t/is (= "query @foo(bar:123){foo}"
             (core/unparse [:oksa/query {:directives [[:foo {:arguments {:bar 123}}]]} [:foo]])))
    (t/is (= "{foo@bar}"
           (core/unparse [[:foo {:directives [:bar]}]])))
    (t/is (= "{foo@bar qux@baz}"
           (core/unparse [[:foo {:directives [:bar]}]
                          [:qux {:directives [:baz]}]])))
    (t/is (= "{foo@foo(bar:123)}"
           (core/unparse [[:foo {:directives [[:foo {:arguments {:bar 123}}]]}]])))
    (t/is (= "{...foo@fooDirective @barDirective}"
             (core/unparse [[:oksa/fragment-spread {:name :foo
                                                    :directives [:fooDirective :barDirective]}]])))
    (t/is (= "{...foo@foo(bar:123)}"
             (core/unparse [[:oksa/fragment-spread {:name :foo
                                                    :directives [[:foo {:arguments {:bar 123}}]]}]])))
    (t/is (= "{...@fooDirective @barDirective{bar}}"
             (core/unparse [[:oksa/inline-fragment {:directives [:fooDirective :barDirective]}
                             [:bar]]])))
    (t/is (= "{...@foo(bar:123){bar}}"
             (core/unparse [[:oksa/inline-fragment {:directives [[:foo {:arguments {:bar 123}}]]}
                             [:bar]]])))
    (t/is (= "fragment foo on Foo@foo(bar:123){bar}"
             (core/unparse [:oksa/fragment {:name :foo
                                            :on :Foo
                                            :directives [[:foo {:arguments {:bar 123}}]]}
                            [:bar]])))
    (t/is (= "fragment foo on Foo@fooDirective @barDirective{bar}"
             (core/unparse [:oksa/fragment {:name :foo
                                            :on :Foo
                                            :directives [:fooDirective :barDirective]}
                            [:bar]])))
    (t/is (= "query ($foo:Bar @fooDirective){fooField}"
             (core/unparse [:oksa/query {:variables [:foo {:directives [:fooDirective]} :Bar]}
                            [:fooField]])))
    (t/is (= "query ($foo:Bar @fooDirective @barDirective){fooField}"
             (core/unparse [:oksa/query {:variables [:foo {:directives [:fooDirective :barDirective]} :Bar]}
                            [:fooField]])))
    (t/is (= "query ($foo:Bar @fooDirective(fooArg:123)){fooField}"
             (core/unparse [:oksa/query {:variables [:foo {:directives [[:fooDirective {:arguments {:fooArg 123}}]]} :Bar]}
                            [:fooField]])))))

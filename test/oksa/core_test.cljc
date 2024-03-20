(ns oksa.core-test
  (:require [#?(:clj clojure.test
                :cljs cljs.test) :as t]
            [oksa.test-util :refer [unparse-and-validate]]
            [oksa.alpha.api :as api])
  #?(:clj (:import [graphql.parser Parser])))

(t/deftest unparse-test
  (t/testing "query"
    (t/is (= "query {foo}" (unparse-and-validate [:oksa/query {} [:foo]])))
    (t/is (= "query {foo bar}" (unparse-and-validate [:oksa/query {} [:foo :bar]])))
    (t/is (= "query {bar{qux{baz}}}" (unparse-and-validate [:oksa/query {} [:bar [:qux [:baz]]]])))
    (t/is (= "query {foo bar{qux{baz}}}" (unparse-and-validate [:oksa/query {} [:foo :bar [:qux [:baz]]]])))
    (t/is (= "query Foo {foo}" (unparse-and-validate [:oksa/query {:name :Foo} [:foo]])))
    (t/testing "non-ambiguity"
      (t/is (thrown? #?(:clj Exception :cljs js/Error) (unparse-and-validate [:oksa/query [:oksa/query [:baz]]])))
      (t/is (= "query {query{baz}}" (unparse-and-validate [:oksa/query [:query [:baz]]])))
      (t/is (= "{query{query{baz}}}" (unparse-and-validate [:query [:query [:baz]]])))))
  (t/testing "mutation"
    (t/is (= "mutation {foo}" (unparse-and-validate [:oksa/mutation {} [:foo]])))
    (t/is (= "mutation {foo bar}" (unparse-and-validate [:oksa/mutation {} [:foo :bar]])))
    (t/is (= "mutation {bar{qux{baz}}}" (unparse-and-validate [:oksa/mutation {} [:bar [:qux [:baz]]]])))
    (t/is (= "mutation {foo bar{qux{baz}}}" (unparse-and-validate [:oksa/mutation {} [:foo :bar [:qux [:baz]]]])))
    (t/is (= "mutation Foo {foo}" (unparse-and-validate [:oksa/mutation {:name :Foo} [:foo]])))
    (t/testing "non-ambiguity"
      (t/is (thrown? #?(:clj Exception :cljs js/Error) (unparse-and-validate [:oksa/mutation [:oksa/mutation [:baz]]])))
      (t/is (= "mutation {mutation{baz}}" (unparse-and-validate [:oksa/mutation [:mutation [:baz]]])))
      (t/is (= "{mutation{mutation{baz}}}" (unparse-and-validate [:mutation [:mutation [:baz]]])))))
  (t/testing "subscription"
    (t/is (= "subscription {foo}" (unparse-and-validate [:oksa/subscription {} [:foo]])))
    (t/is (= "subscription {foo bar}" (unparse-and-validate [:oksa/subscription {} [:foo :bar]])))
    (t/is (= "subscription {bar{qux{baz}}}" (unparse-and-validate [:oksa/subscription {} [:bar [:qux [:baz]]]])))
    (t/is (= "subscription {foo bar{qux{baz}}}" (unparse-and-validate [:oksa/subscription {} [:foo :bar [:qux [:baz]]]])))
    (t/is (= "subscription Foo {foo}" (unparse-and-validate [:oksa/subscription {:name :Foo} [:foo]])))
    (t/testing "non-ambiguity"
      (t/is (thrown? #?(:clj Exception :cljs js/Error) (unparse-and-validate [:oksa/subscription [:oksa/subscription [:baz]]])))
      (t/is (= "subscription {subscription{baz}}" (unparse-and-validate [:oksa/subscription [:subscription [:baz]]])))
      (t/is (= "{subscription{subscription{baz}}}" (unparse-and-validate [:subscription [:subscription [:baz]]])))))
  (t/testing "selection set"
    (t/is (= "{foo}"
             (unparse-and-validate [:foo])))
    (t/is (= "{foo bar}"
             (unparse-and-validate [:foo :bar])))
    (t/is (= "{bar{qux{baz}}}"
             (unparse-and-validate [:bar [:qux [:baz]]])))
    (t/is (= "{foo bar{qux{baz}}}"
             (unparse-and-validate [:foo :bar [:qux [:baz]]])))
    (t/is (= "{foo bar{qux baz}}"
             (unparse-and-validate [:foo :bar [:qux :baz]])))
    (t/is (= "{foo{bar{baz qux} frob}}"
             (unparse-and-validate [:foo [:bar [:baz :qux] :frob]])))
    (t/testing "support strings as field names"
      (t/is (= "{foo}"
               (unparse-and-validate ["foo"])
               (unparse-and-validate [:foo]))))
    (t/testing "arguments"
      (t/is (= "{foo}"
               (unparse-and-validate [[:foo {:arguments {}}]])))
      (t/is (= "{foo(a:1, b:\"hello world\", c:true, d:null, e:foo, f:[1 2 3], g:{frob:{foo:1, bar:2}}, h:$fooVar)}"
               (unparse-and-validate [[:foo {:arguments {:a 1
                                                         :b "hello world"
                                                         :c true
                                                         :d nil
                                                         :e :foo
                                                         :f [1 2 3]
                                                         :g {:frob {:foo 1
                                                                    :bar 2}}
                                                         :h :$fooVar}}]])))
      #?(:clj (t/is (= "{foo(a:0.3333333333333333)}"
                       (unparse-and-validate [[:foo {:arguments {:a 1/3}}]]))))
      (t/testing "escaping special characters"
        (t/is (= "{fooField(foo:\"\\\"\")}"
                 (unparse-and-validate [[:fooField {:arguments {:foo "\""}}]])))
        (t/is (= "{fooField(foo:\"\\\\\")}"
                 (unparse-and-validate [[:fooField {:arguments {:foo "\\"}}]])))
        (t/is (= "{fooField(foo:\"foo\\b\\f\\r\\n\\tbar\")}"
                 (unparse-and-validate [[:fooField {:arguments {:foo (str "foo\b\f\r\n\tbar")}}]]))))))
  (t/testing "document"
    (t/is (= "{foo}"
             (unparse-and-validate [:<> [:foo]])
             (unparse-and-validate [:oksa/document [:foo]])))
    (t/is (= "{foo}\n{bar}"
             (unparse-and-validate [:<> [:foo] [:bar]])
             (unparse-and-validate [:oksa/document [:foo] [:bar]])))
    (t/is (= "{foo}\nquery {bar}\nmutation {qux}\nsubscription {baz}\nfragment foo on Foo{bar}"
             (unparse-and-validate [:<>
                                    [:foo]
                                    [:oksa/query [:bar]]
                                    [:oksa/mutation [:qux]]
                                    [:oksa/subscription [:baz]]
                                    [:oksa/fragment {:name :foo :on :Foo} [:bar]]])
             (unparse-and-validate [:oksa/document
                                    [:foo]
                                    [:oksa/query [:bar]]
                                    [:oksa/mutation [:qux]]
                                    [:oksa/subscription [:baz]]
                                    [:oksa/fragment {:name :foo :on :Foo} [:bar]]])))
    (t/testing "non-ambiguity"
      (t/is (thrown? #?(:clj Exception :cljs js/Error) (unparse-and-validate [:oksa/document [:oksa/document [:baz]]])))
      (t/is (= "{document{baz}}" (unparse-and-validate [:oksa/document [:document [:baz]]])))
      (t/is (= "{document{document{baz}}}" (unparse-and-validate [:document [:document [:baz]]])))))
  (t/testing "fragment"
    (t/is (= "fragment Foo on Bar{foo}"
             (unparse-and-validate [:# {:name :Foo :on :Bar} [:foo]])
             (unparse-and-validate [:oksa/fragment {:name :Foo :on :Bar} [:foo]])))
    (t/is (= "fragment Foo on Bar{foo bar}"
             (unparse-and-validate [:# {:name :Foo :on :Bar} [:foo :bar]])
             (unparse-and-validate [:oksa/fragment {:name :Foo :on :Bar} [:foo :bar]])))
    (t/is (= "fragment Foo on Bar{bar{qux{baz}}}"
             (unparse-and-validate [:# {:name :Foo :on :Bar} [:bar [:qux [:baz]]]])
             (unparse-and-validate [:oksa/fragment {:name :Foo :on :Bar} [:bar [:qux [:baz]]]])))
    (t/is (= "fragment Foo on Bar{foo bar{qux{baz}}}"
             (unparse-and-validate [:# {:name :Foo :on :Bar} [:foo :bar [:qux [:baz]]]])
             (unparse-and-validate [:oksa/fragment {:name :Foo :on :Bar} [:foo :bar [:qux [:baz]]]])))
    (t/testing "non-ambiguity"
      (t/is (thrown? #?(:clj Exception :cljs js/Error) (unparse-and-validate [:oksa/fragment {:name :Foo :on :Bar} [:oksa/fragment {:name :Foo :on :Bar} [:baz]]])))
      (t/is (= "fragment Foo on Bar{fragment{baz}}" (unparse-and-validate [:oksa/fragment {:name :Foo :on :Bar} [:fragment [:baz]]])))
      (t/is (= "{fragment{fragment{baz}}}" (unparse-and-validate [:fragment [:fragment [:baz]]])))))
  (t/testing "fragment spread"
    (t/is (= "{foo ...bar}"
             (unparse-and-validate [:foo [:... {:name :bar}]])
             (unparse-and-validate [:foo [:oksa/fragment-spread {:name :bar}]]))))
  (t/testing "inline fragment"
    (t/is (= "{foo ...{bar}}"
             (unparse-and-validate [:foo [:... [:bar]]])
             (unparse-and-validate [:foo [:oksa/inline-fragment [:bar]]])))
    (t/is (= "{foo ...on Bar{bar}}"
             (unparse-and-validate [:foo [:... {:on :Bar} [:bar]]])
             (unparse-and-validate [:foo [:oksa/inline-fragment {:on :Bar} [:bar]]]))))
  (t/testing "variable definitions"
    (t/testing "named type"
      (t/is (= "query ($fooVar:FooType){fooField}"
               (unparse-and-validate [:oksa/query {:variables [:fooVar :FooType]}
                                      [:fooField]]))))
    (t/testing "non-null named type")
    (t/is (= "query ($fooVar:FooType!){fooField}"
             (unparse-and-validate [:oksa/query {:variables [:fooVar [:FooType {:non-null true}]]}
                                    [:fooField]])))
    (t/testing "named type within list"
      (t/is (= "query ($fooVar:[FooType]){fooField}"
               (unparse-and-validate [:oksa/query {:variables [:fooVar [:oksa/list :FooType]]}
                                      [:fooField]])
               (unparse-and-validate [:oksa/query {:variables [:fooVar [:FooType]]}
                                      [:fooField]]))))
    (t/testing "non-null named type within list"
      (t/is (= "query ($fooVar:[FooType!]){fooField}"
               (unparse-and-validate [:oksa/query {:variables [:fooVar [:oksa/list
                                                                        [:FooType {:non-null true}]]]}
                                      [:fooField]])
               (unparse-and-validate [:oksa/query {:variables [:fooVar [:FooType!]]}
                                      [:fooField]]))))
    (t/testing "named type within list"
      (t/is (= "query ($fooVar:[BarType]!){fooField}"
               (unparse-and-validate [:oksa/query {:variables [:fooVar [:oksa/list {:non-null true}
                                                                        :BarType]]}
                                      [:fooField]])
               (unparse-and-validate [:oksa/query {:variables [:fooVar [:! :BarType]]}
                                      [:fooField]]))))
    (t/testing "non-null type within non-null list"
      (t/is (= "query ($fooVar:[BarType!]!){fooField}"
               (unparse-and-validate [:oksa/query {:variables [:fooVar [:oksa/list {:non-null true}
                                                                        [:BarType {:non-null true}]]]}
                                      [:fooField]])
               (unparse-and-validate [:oksa/query {:variables [:fooVar [:! :BarType!]]}
                                      [:fooField]]))))
    (t/testing "named type within list within list"
      (t/is (= "query ($fooVar:[[BarType]]){fooField}"
               (unparse-and-validate [:oksa/query {:variables [:fooVar [:oksa/list
                                                                        [:oksa/list
                                                                         :BarType]]]}
                                      [:fooField]])
               (unparse-and-validate [:oksa/query {:variables [:fooVar [[:BarType]]]}
                                      [:fooField]]))))
    (t/testing "non-null list within non-null list"
      (t/is (= "query ($fooVar:[[BarType]!]!){fooField}"
               (unparse-and-validate [:oksa/query {:variables [:fooVar [:oksa/list {:non-null true}
                                                                        [:oksa/list {:non-null true}
                                                                         :BarType]]]}
                                      [:fooField]])
               (unparse-and-validate [:oksa/query {:variables [:fooVar [:! [:! :BarType]]]}
                                      [:fooField]]))))
    (t/testing "multiple variable definitions"
      (t/is (= "query ($fooVar:FooType,$barVar:BarType){fooField}"
               (unparse-and-validate [:oksa/query {:variables [:fooVar :FooType
                                                               :barVar :BarType]}
                                      [:fooField]]))))
    (t/testing "default values"
      (t/is (= "query ($fooVar:Foo=123){fooField(foo:$fooVar)}"
               (unparse-and-validate [:oksa/query {:variables [:$fooVar {:default 123} :Foo]}
                                      [[:fooField {:arguments {:foo :$fooVar}}]]])))
      #?(:clj
         (t/is (= "query ($fooVar:Foo=0.3333333333333333){fooField(foo:$fooVar)}"
                  (unparse-and-validate [:oksa/query {:variables [:$fooVar {:default 1/3} :Foo]}
                                         [[:fooField {:arguments {:foo :$fooVar}}]]]))))
      (t/is (= "query ($fooVar:Foo=\"häkkyrä\"){fooField(foo:$fooVar)}"
               (unparse-and-validate [:oksa/query {:variables [:$fooVar {:default "häkkyrä"} :Foo]}
                                      [[:fooField {:arguments {:foo :$fooVar}}]]])))
      (t/is (= "query ($fooVar:Foo=true){fooField(foo:$fooVar)}"
               (unparse-and-validate [:oksa/query {:variables [:$fooVar {:default true} :Foo]}
                                      [[:fooField {:arguments {:foo :$fooVar}}]]])))
      (t/is (= "query ($fooVar:Foo=null){fooField(foo:$fooVar)}"
               (unparse-and-validate [:oksa/query {:variables [:$fooVar {:default nil} :Foo]}
                                      [[:fooField {:arguments {:foo :$fooVar}}]]])))
      (t/is (= "query ($fooVar:Foo=Frob){fooField(foo:$fooVar)}"
               (unparse-and-validate [:oksa/query {:variables [:$fooVar {:default :Frob} :Foo]}
                                      [[:fooField {:arguments {:foo :$fooVar}}]]])))
      (t/is (= "query ($fooVar:Foo=[1 \"häkkyrä\" true null Bar [1 \"häkkyrä\" true null Bar [\"foo\" \"bar\"]] {foo:\"kikka\", bar:\"kukka\"}]){fooField(foo:$fooVar)}"
               (unparse-and-validate [:oksa/query {:variables [:$fooVar {:default [1
                                                                                   "häkkyrä"
                                                                                   true
                                                                                   nil
                                                                                   :Bar
                                                                                   [1 "häkkyrä" true nil :Bar ["foo" "bar"]]
                                                                                   {:foo "kikka"
                                                                                    "bar" "kukka"}]}
                                                               :Foo]}
                                      [[:fooField {:arguments {:foo :$fooVar}}]]])))
      (t/is (= "query ($fooVar:Foo={number:1, string:\"häkkyrä\", boolean:true, default:null, keyword:Bar, coll:[1 \"häkkyrä\" true null Bar [\"foo\" \"bar\"]]}){fooField(foo:$fooVar)}"
               (unparse-and-validate [:oksa/query {:variables [:$fooVar {:default {:number 1
                                                                                   :string "häkkyrä"
                                                                                   :boolean true
                                                                                   :default nil
                                                                                   :keyword :Bar
                                                                                   :coll [1 "häkkyrä" true nil :Bar ["foo" "bar"]]}} :Foo]}
                                      [[:fooField {:arguments {:foo :$fooVar}}]]])))))
  (t/testing "variable names"
    (doseq [variable-name [:fooVar :$fooVar "fooVar" "$fooVar"]]
      (t/is (= "query ($fooVar:FooType){fooField}"
               (unparse-and-validate [:oksa/query {:variables [variable-name :FooType]}
                                      [:fooField]])))))
  (t/testing "aliases"
    (t/is (= "{bar:foo}"
             (unparse-and-validate [[:foo {:alias "bar"}]])
             (unparse-and-validate [[:foo {:alias :bar}]])))
    (t/is (= "{bar:foo baz:qux}"
             (unparse-and-validate [[:foo {:alias "bar"}]
                                    [:qux {:alias "baz"}]])
             (unparse-and-validate [[:foo {:alias :bar}]
                                    [:qux {:alias :baz}]])))
    (t/is (= "{bar:foo frob baz:qux}"
             (unparse-and-validate [[:foo {:alias "bar"}]
                                    :frob
                                    [:qux {:alias "baz"}]])
             (unparse-and-validate [[:foo {:alias :bar}]
                                    :frob
                                    [:qux {:alias :baz}]]))))
  (t/testing "directives"
    (t/is (= "query @foo{foo}"
             (unparse-and-validate [:oksa/query {:directives [:foo]} [:foo]])
             (unparse-and-validate [:oksa/query {:directives [[:foo]]} [:foo]])))
    (t/is (= "query @foo @bar{foo}"
             (unparse-and-validate [:oksa/query {:directives [:foo :bar]} [:foo]])
             (unparse-and-validate [:oksa/query {:directives [[:foo] [:bar]]} [:foo]])))
    (t/is (= "query @foo(bar:123){foo}"
             (unparse-and-validate [:oksa/query {:directives [[:foo {:arguments {:bar 123}}]]} [:foo]])))
    (t/is (= "{foo@bar}"
             (unparse-and-validate [[:foo {:directives [:bar]}]])))
    (t/is (= "{foo@bar qux@baz}"
             (unparse-and-validate [[:foo {:directives [:bar]}]
                                    [:qux {:directives [:baz]}]])))
    (t/is (= "{foo@foo(bar:123)}"
             (unparse-and-validate [[:foo {:directives [[:foo {:arguments {:bar 123}}]]}]])))
    (t/is (= "{...foo@fooDirective @barDirective}"
             (unparse-and-validate [[:oksa/fragment-spread {:name :foo
                                                            :directives [:fooDirective :barDirective]}]])))
    (t/is (= "{...foo@foo(bar:123)}"
             (unparse-and-validate [[:oksa/fragment-spread {:name :foo
                                                            :directives [[:foo {:arguments {:bar 123}}]]}]])))
    (t/is (= "{...@fooDirective @barDirective{bar}}"
             (unparse-and-validate [[:oksa/inline-fragment {:directives [:fooDirective :barDirective]}
                                     [:bar]]])))
    (t/is (= "{...@foo(bar:123){bar}}"
             (unparse-and-validate [[:oksa/inline-fragment {:directives [[:foo {:arguments {:bar 123}}]]}
                                     [:bar]]])))
    (t/is (= "fragment foo on Foo@foo(bar:123){bar}"
             (unparse-and-validate [:oksa/fragment {:name :foo
                                                    :on :Foo
                                                    :directives [[:foo {:arguments {:bar 123}}]]}
                                    [:bar]])))
    (t/is (= "fragment foo on Foo@fooDirective @barDirective{bar}"
             (unparse-and-validate [:oksa/fragment {:name :foo
                                                    :on :Foo
                                                    :directives [:fooDirective :barDirective]}
                                    [:bar]])))
    (t/is (= "query ($foo:Bar @fooDirective){fooField}"
             (unparse-and-validate [:oksa/query {:variables [:foo {:directives [:fooDirective]} :Bar]}
                                    [:fooField]])))
    (t/is (= "query ($foo:Bar @fooDirective @barDirective){fooField}"
             (unparse-and-validate [:oksa/query {:variables [:foo {:directives [:fooDirective :barDirective]} :Bar]}
                                    [:fooField]])))
    (t/is (= "query ($foo:Bar @fooDirective(fooArg:123)){fooField}"
             (unparse-and-validate [:oksa/query {:variables [:foo {:directives [[:fooDirective {:arguments {:fooArg 123}}]]} :Bar]}
                                    [:fooField]])))))

(t/deftest gql-test
  (t/is (= "{foo}\nquery {bar}\nmutation {qux}\nsubscription {baz}\nfragment foo on Foo{bar}\n{foo2}\nquery {bar2}\nmutation {qux2}\nsubscription {baz2}\nfragment foo2 on Foo2{bar2}"
           (oksa.core/gql
            (api/document
             (api/select :foo)
             (api/query (api/select :bar))
             (api/mutation (api/select :qux))
             (api/subscription (api/select :baz))
             (api/fragment (api/opts (api/name :foo)
                                     (api/on :Foo))
                           (api/select :bar)))
            [:oksa/document
             [:foo2]
             [:oksa/query [:bar2]]
             [:oksa/mutation [:qux2]]
             [:oksa/subscription [:baz2]]
             [:oksa/fragment {:name :foo2 :on :Foo2} [:bar2]]]))))

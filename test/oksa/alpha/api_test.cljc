(ns oksa.alpha.api-test
  (:require [camel-snake-kebab.core :as csk]
            [#?(:clj clojure.test
                :cljs cljs.test) :as t]
            [oksa.alpha.api :as api]
            [oksa.core])
  #?(:clj (:import [graphql.parser Parser])))

(defn unparse-and-validate
  ([x]
   (unparse-and-validate nil x))
  ([opts x]
   (let [graphql-query (api/gql opts x)]
     #?(:clj (Parser/parse graphql-query))
     graphql-query)))

(t/deftest unparse-test
  (t/testing "query"
    (t/is (= "query {foo}"
             (unparse-and-validate (api/query (api/select :foo)))))
    (t/is (= "query {foo bar}"
             (unparse-and-validate (api/query (api/select :foo :bar)))))
    (t/is (= "query {bar{qux{baz}}}"
             (unparse-and-validate
              (api/query
               (api/select :bar
                 (api/select :qux
                   (api/select :baz)))))))
    (t/is (= "query {foo bar{qux{baz}}}"
             (unparse-and-validate
              (api/query
               (api/select :foo :bar
                 (api/select :qux
                   (api/select :baz)))))))
    (t/is (= "query Foo {foo}"
             (unparse-and-validate (api/query (api/opts (api/name :Foo)) (api/select :foo)))))
    (t/testing "non-ambiguity"
      (t/is (thrown? #?(:clj Exception :cljs js/Error) (unparse-and-validate (api/query (api/query (api/select :baz))))))
      (t/is (= "query {query{baz}}"
               (unparse-and-validate
                (api/query (api/select :query (api/select :baz))))))
      (t/is (= "{query{query{baz}}}" (unparse-and-validate
                                      (api/select :query
                                        (api/select :query
                                          (api/select :baz))))))))
  (t/testing "mutation"
    (t/is (= "mutation {foo}" (unparse-and-validate (api/mutation (api/select :foo)))))
    (t/is (= "mutation {foo bar}" (unparse-and-validate (api/mutation (api/select :foo :bar)))))
    (t/is (= "mutation {bar{qux{baz}}}" (unparse-and-validate
                                         (api/mutation
                                          (api/select :bar
                                            (api/select :qux
                                              (api/select :baz)))))))
    (t/is (= "mutation {foo bar{qux{baz}}}" (unparse-and-validate
                                             (api/mutation
                                              (api/select :foo :bar
                                                (api/select :qux
                                                  (api/select :baz)))))))
    (t/is (= "mutation Foo {foo}"
             (unparse-and-validate
              (api/mutation (api/opts (api/name :Foo)) (api/select :foo)))))
    (t/testing "non-ambiguity"
      (t/is (thrown? #?(:clj Exception :cljs js/Error) (unparse-and-validate (api/mutation (api/mutation (api/select :baz))))))
      (t/is (= "mutation {mutation{baz}}" (unparse-and-validate
                                           (api/mutation (api/select :mutation (api/select :baz))))))
      (t/is (= "{mutation{mutation{baz}}}" (unparse-and-validate
                                            (api/select :mutation
                                              (api/select :mutation
                                                (api/select :baz))))))))
  (t/testing "subscription"
    (t/is (= "subscription {foo}" (unparse-and-validate (api/subscription (api/select :foo)))))
    (t/is (= "subscription {foo bar}" (unparse-and-validate (api/subscription (api/select :foo :bar)))))
    (t/is (= "subscription {bar{qux{baz}}}" (unparse-and-validate
                                             (api/subscription
                                              (api/select :bar
                                                (api/select :qux
                                                  (api/select :baz)))))))
    (t/is (= "subscription {foo bar{qux{baz}}}" (unparse-and-validate
                                                 (api/subscription
                                                  (api/select :foo :bar
                                                    (api/select :qux
                                                      (api/select :baz)))))))
    (t/is (= "subscription Foo {foo}" (unparse-and-validate (api/subscription (api/opts (api/name :Foo)) (api/select :foo)))))
    (t/testing "non-ambiguity"
      (t/is (thrown? #?(:clj Exception :cljs js/Error) (unparse-and-validate (api/subscription (api/subscription (api/select :baz))))))
      (t/is (= "subscription {subscription{baz}}" (unparse-and-validate
                                                   (api/subscription (api/select :subscription (api/select :baz))))))
      (t/is (= "{subscription{subscription{baz}}}" (unparse-and-validate
                                                    (api/select :subscription
                                                      (api/select :subscription
                                                        (api/select :baz))))))))
  (t/testing "selection set"
    (t/is (= "{foo}"
             (unparse-and-validate (api/select :foo))
             (unparse-and-validate (api/select (api/field :foo)))))
    (t/is (= "{foo bar}"
             (unparse-and-validate (api/select
                                     (api/field :foo)
                                     (api/field :bar)))
             (unparse-and-validate (api/select :foo :bar))))
    (t/is (= "{bar{qux{baz}}}"
             (unparse-and-validate (api/select :bar
                                     (api/select :qux
                                      (api/select :baz))))
             (unparse-and-validate (api/select (api/field :bar)
                                     (api/select (api/field :qux)
                                       (api/select (api/field :baz)))))
             (unparse-and-validate (api/select
                                     (api/field :bar
                                       (api/select
                                         (api/field :qux
                                           (api/select :baz))))))))
    (t/is (= "{foo bar{qux{baz}}}"
             (unparse-and-validate (api/select :foo :bar
                                     (api/select :qux
                                       (api/select :baz))))
             (unparse-and-validate (api/select :foo
                                     (api/field :bar
                                       (api/select
                                         (api/field :qux
                                           (api/select :baz))))))))
    (t/is (= "{foo bar{qux baz}}"
             (unparse-and-validate (api/select :foo :bar
                                     (api/select :qux :baz)))
             (unparse-and-validate (api/select :foo
                                     (api/field :bar
                                       (api/select
                                         (api/field :qux)
                                         (api/field :baz)))))))
    (t/is (= "{foo{bar{baz qux} frob}}"
             (unparse-and-validate (api/select :foo
                                    (api/select :bar
                                      (api/select :baz :qux)
                                      :frob)))
             (unparse-and-validate (api/select
                                     (api/field :foo
                                       (api/select
                                         (api/field :bar
                                           (api/select
                                             (api/field :baz)
                                             (api/field :qux)))
                                         :frob))))))
    (t/testing "support strings as field names"
      (t/is (= "{foo}"
               (unparse-and-validate (api/select "foo"))
               (unparse-and-validate (api/select :foo))
               (unparse-and-validate (api/select (api/field "foo")))
               (unparse-and-validate (api/select (api/field :foo))))))
    (t/testing "arguments"
      (t/is (= "{foo}"
               (unparse-and-validate (api/select (api/field :foo (api/opts (api/arguments)))))))
      (t/is (= "{foo(a:1, b:\"hello world\", c:true, d:null, e:foo, f:[1 2 3], g:{frob:{foo:1, bar:2}}, h:$fooVar)}"
               (unparse-and-validate (api/select (api/field :foo
                                                   (api/opts
                                                    (api/arguments :a 1
                                                                   :b "hello world"
                                                                   :c true
                                                                   :d nil
                                                                   :e :foo
                                                                   :f [1 2 3]
                                                                   :g {:frob {:foo 1
                                                                              :bar 2}}
                                                                   :h :$fooVar)))))
               (unparse-and-validate (api/select (api/field :foo
                                                   (api/opts
                                                    (api/argument :a 1)
                                                    (api/argument :b "hello world")
                                                    (api/argument :c true)
                                                    (api/argument :d nil)
                                                    (api/argument :e :foo)
                                                    (api/argument :f [1 2 3])
                                                    (api/argument :g {:frob {:foo 1
                                                                             :bar 2}})
                                                    (api/argument :h :$fooVar)))))))
      #?(:clj (t/is (= "{foo(a:0.3333333333333333)}"
                       (unparse-and-validate (api/select (api/field :foo (api/opts (api/argument :a 1/3)))))
                       (unparse-and-validate (api/select (api/field :foo (api/opts (api/arguments :a 1/3))))))))
      (t/testing "escaping special characters"
        (t/is (= "{fooField(foo:\"\\\"\")}"
                 (unparse-and-validate (api/select (api/field :fooField (api/opts (api/argument :foo "\"")))))
                 (unparse-and-validate (api/select (api/field :fooField (api/opts (api/arguments :foo "\"")))))))
        (t/is (= "{fooField(foo:\"\\\\\")}"
                 (unparse-and-validate (api/select (api/field :fooField (api/opts (api/argument :foo "\\")))))
                 (unparse-and-validate (api/select (api/field :fooField (api/opts (api/arguments :foo "\\")))))))
        (t/is (= "{fooField(foo:\"foo\\b\\f\\r\\n\\tbar\")}"
                 (unparse-and-validate (api/select (api/field :fooField (api/opts (api/argument :foo "foo\b\f\r\n\tbar")))))
                 (unparse-and-validate (api/select (api/field :fooField (api/opts (api/arguments :foo "foo\b\f\r\n\tbar"))))))))))
  (t/testing "document"
    (t/is (= "{foo}"
             (unparse-and-validate (api/document (api/select :foo)))))
    (t/is (= "{foo}\n{bar}"
             (unparse-and-validate (api/document (api/select :foo) (api/select :bar)))))
    (t/is (= "{foo}\nquery {bar}\nmutation {qux}\nsubscription {baz}\nfragment foo on Foo{bar}"
             (unparse-and-validate (api/document
                                    (api/select :foo)
                                    (api/query (api/select :bar))
                                    (api/mutation (api/select :qux))
                                    (api/subscription (api/select :baz))
                                    (api/fragment (api/opts (api/name :foo)
                                                            (api/on :Foo))
                                      (api/select :bar))))))
    (t/testing "non-ambiguity"
      (t/is (thrown? #?(:clj Exception :cljs js/Error) (unparse-and-validate (api/document (api/document (api/select :baz))))))
      (t/is (= "{document{baz}}" (unparse-and-validate (api/document (api/select :document (api/select :baz))))))
      (t/is (= "{document{document{baz}}}" (unparse-and-validate (api/select :document (api/select :document (api/select :baz))))))))
  (t/testing "fragment"
    (t/is (= "fragment Foo on Bar{foo}"
             (unparse-and-validate (api/fragment (api/opts (api/name :Foo)
                                                           (api/on :Bar))
                                     (api/select :foo)))))
    (t/is (= "fragment Foo on Bar{foo bar}"
             (unparse-and-validate (api/fragment (api/opts (api/name :Foo)
                                                           (api/on :Bar))
                                     (api/select :foo :bar)))))
    (t/is (= "fragment Foo on Bar{bar{qux{baz}}}"
             (unparse-and-validate (api/fragment (api/opts (api/name :Foo)
                                                           (api/on :Bar))
                                     (api/select :bar
                                       (api/select :qux
                                         (api/select :baz)))))))
    (t/is (= "fragment Foo on Bar{foo bar{qux{baz}}}"
             (unparse-and-validate (api/fragment (api/opts (api/name :Foo)
                                                           (api/on :Bar))
                                     (api/select :foo :bar
                                       (api/select :qux
                                         (api/select :baz)))))))
    (t/testing "non-ambiguity"
      (t/is (thrown? #?(:clj Exception :cljs js/Error)
                     (unparse-and-validate
                      (api/fragment (api/opts (api/name :Foo) (api/on :Bar))
                        (api/fragment (api/opts (api/name :Foo) (api/on :Bar))
                          (api/select :baz))))))
      (t/is (= "fragment Foo on Bar{fragment{baz}}"
               (unparse-and-validate
                (api/fragment (api/opts (api/name :Foo) (api/on :Bar))
                  (api/select :fragment
                    (api/select :baz))))))
      (t/is (= "{fragment{fragment{baz}}}"
               (unparse-and-validate (api/select :fragment
                                       (api/select :fragment
                                         (api/select :baz))))))))
  (t/testing "fragment spread"
    (t/is (= "{foo ...bar}"
             (unparse-and-validate (api/select :foo
                                     (api/fragment-spread (api/opts (api/name :bar))))))))
  (t/testing "inline fragment"
    (t/is (= "{foo ...{bar}}"
             (unparse-and-validate (api/select :foo
                                     (api/inline-fragment
                                      (api/select :bar))))))
    (t/is (= "{foo ...on Bar{bar}}"
             (unparse-and-validate (api/select :foo
                                     (api/inline-fragment (api/opts (api/on :Bar))
                                       (api/select :bar))))))
    (t/is (= "{...on Foobar{foo bar ...on Frobnitz{frob nitz}}}"
             (unparse-and-validate
              (api/select
               (api/inline-fragment (api/opts (api/on :Foobar))
                 (api/select :foo
                             :bar
                             (api/inline-fragment (api/opts (api/on :Frobnitz))
                               (api/select :frob :nitz)))))))))
  (t/testing "variable definitions"
    (t/testing "named type"
      (t/is (= "query ($fooVar:FooType){fooField}"
               (unparse-and-validate (api/query (api/opts (api/variable :fooVar :FooType))
                                       (api/select :fooField)))
               (unparse-and-validate (api/query (api/opts (api/variables :fooVar (api/type :FooType)))
                                                (api/select :fooField)))
               (unparse-and-validate (api/query (api/opts (api/variables :fooVar :FooType))
                                       (api/select :fooField))))))
    (t/testing "non-null named type")
    (t/is (= "query ($fooVar:FooType!){fooField}"
             (unparse-and-validate (api/query (api/opts (api/variable :fooVar (api/type! :FooType)))
                                     (api/select :fooField)))
             (unparse-and-validate (api/query (api/opts (api/variables :fooVar (api/type! :FooType)))
                                     (api/select :fooField)))))
    (t/testing "named type within list"
      (t/is (= "query ($fooVar:[FooType]){fooField}"
               (unparse-and-validate (api/query (api/opts (api/variable :fooVar (api/list :FooType)))
                                       (api/select :fooField)))
               (unparse-and-validate (api/query (api/opts (api/variables :fooVar (api/list :FooType)))
                                       (api/select :fooField))))))
    (t/testing "non-null named type within list"
      (t/is (= "query ($fooVar:[FooType!]){fooField}"
               (unparse-and-validate (api/query (api/opts (api/variable :fooVar (api/list (api/type! :FooType))))
                                       (api/select :fooField)))
               (unparse-and-validate (api/query (api/opts (api/variables :fooVar (api/list (api/type! :FooType))))
                                       (api/select :fooField))))))
    (t/testing "named type within list"
      (t/is (= "query ($fooVar:[BarType]!){fooField}"
               (unparse-and-validate (api/query (api/opts (api/variable :fooVar (api/list! :BarType)))
                                       (api/select :fooField)))
               (unparse-and-validate (api/query (api/opts (api/variables :fooVar (api/list! :BarType)))
                                       (api/select :fooField))))))
    (t/testing "non-null type within non-null list"
      (t/is (= "query ($fooVar:[BarType!]!){fooField}"
               (unparse-and-validate (api/query (api/opts (api/variable :fooVar (api/list! (api/type! :BarType))))
                                       (api/select :fooField)))
               (unparse-and-validate (api/query (api/opts (api/variables :fooVar (api/list! (api/type! :BarType))))
                                       (api/select :fooField))))))
    (t/testing "named type within list within list"
      (t/is (= "query ($fooVar:[[BarType]]){fooField}"
               (unparse-and-validate (api/query (api/opts (api/variable :fooVar (api/list (api/list :BarType))))
                                       (api/select :fooField)))
               (unparse-and-validate (api/query (api/opts (api/variables :fooVar (api/list (api/list :BarType))))
                                       (api/select :fooField))))))
    (t/testing "non-null list within non-null list"
      (t/is (= "query ($fooVar:[[BarType]!]!){fooField}"
               (unparse-and-validate (api/query (api/opts (api/variable :fooVar (api/list! (api/list! :BarType))))
                                       (api/select :fooField)))
               (unparse-and-validate (api/query (api/opts (api/variables :fooVar (api/list! (api/list! :BarType))))
                                       (api/select :fooField))))))
    (t/testing "multiple variable definitions"
      (t/is (= "query ($fooVar:FooType,$barVar:BarType){fooField}"
               (unparse-and-validate (api/query (api/opts (api/variable :fooVar :FooType)
                                                          (api/variable :barVar :BarType))
                                       (api/select :fooField)))
               (unparse-and-validate (api/query (api/opts (api/variables :fooVar :FooType
                                                                         :barVar :BarType))
                                       (api/select :fooField))))))
    (t/testing "default values"
      (let [graphql-query (fn [default-value]
                            (api/query (api/opts (api/variable :fooVar (api/opts (api/default default-value)) :Foo))
                              (api/select
                                (api/field :fooField (api/opts (api/argument :foo :$fooVar))))))]
        (t/is (= "query ($fooVar:Foo=123){fooField(foo:$fooVar)}"
                 (unparse-and-validate (graphql-query 123))))
        #?(:clj
           (t/is (= "query ($fooVar:Foo=0.3333333333333333){fooField(foo:$fooVar)}"
                    (unparse-and-validate (graphql-query 1/3)))))
        (t/is (= "query ($fooVar:Foo=\"häkkyrä\"){fooField(foo:$fooVar)}"
                 (unparse-and-validate (graphql-query "häkkyrä"))))
        (t/is (= "query ($fooVar:Foo=true){fooField(foo:$fooVar)}"
                 (unparse-and-validate (graphql-query true))))
        (t/is (= "query ($fooVar:Foo=null){fooField(foo:$fooVar)}"
                 (unparse-and-validate (graphql-query nil))))
        (t/is (= "query ($fooVar:Foo=Frob){fooField(foo:$fooVar)}"
                 (unparse-and-validate (graphql-query :Frob))))
        (t/is (= "query ($fooVar:Foo=[1 \"häkkyrä\" true null Bar [1 \"häkkyrä\" true null Bar [\"foo\" \"bar\"]] {foo:\"kikka\", bar:\"kukka\"}]){fooField(foo:$fooVar)}"
                 (unparse-and-validate (graphql-query [1
                                                       "häkkyrä"
                                                       true
                                                       nil
                                                       :Bar
                                                       [1 "häkkyrä" true nil :Bar ["foo" "bar"]]
                                                       {:foo "kikka"
                                                        "bar" "kukka"}]))))
        (t/is (= "query ($fooVar:Foo={number:1, string:\"häkkyrä\", boolean:true, default:null, keyword:Bar, coll:[1 \"häkkyrä\" true null Bar [\"foo\" \"bar\"]]}){fooField(foo:$fooVar)}"
                 (unparse-and-validate (graphql-query {:number 1
                                                       :string "häkkyrä"
                                                       :boolean true
                                                       :default nil
                                                       :keyword :Bar
                                                       :coll [1 "häkkyrä" true nil :Bar ["foo" "bar"]]})))))))
  (t/testing "variable names"
    (doseq [variable-name [:fooVar :$fooVar "fooVar" "$fooVar"]]
      (t/is (= "query ($fooVar:FooType){fooField}"
               (unparse-and-validate (api/query (api/opts (api/variable variable-name :FooType))
                                       (api/select :fooField)))
               (unparse-and-validate (api/query (api/opts (api/variables variable-name :FooType))
                                       (api/select :fooField)))))))
  (t/testing "aliases"
    (t/is (= "{bar:foo}"
             (unparse-and-validate (api/select (api/field :foo (api/opts (api/alias :bar)))))
             (unparse-and-validate (api/select (api/field :foo (api/opts (api/alias "bar")))))))
    (t/is (= "{bar:foo baz:qux}"
             (unparse-and-validate
              (api/select
                (api/field :foo (api/opts (api/alias :bar)))
                (api/field :qux (api/opts (api/alias :baz)))))
             (unparse-and-validate
              (api/select
                (api/field :foo (api/opts (api/alias "bar")))
                (api/field :qux (api/opts (api/alias "baz")))))))
    (t/is (= "{bar:foo frob baz:qux}"
             (unparse-and-validate
              (api/select
                (api/field :foo (api/opts (api/alias :bar)))
                :frob
                (api/field :qux (api/opts (api/alias :baz)))))
             (unparse-and-validate
              (api/select
                (api/field :foo (api/opts (api/alias "bar")))
                :frob
                (api/field :qux (api/opts (api/alias "baz"))))))))
  (t/testing "directives"
    (t/is (= "query @foo{foo}"
             (unparse-and-validate (api/query (api/opts (api/directives :foo))
                                     (api/select :foo)))))
    (t/is (= "query @foo @bar{foo}"
             (unparse-and-validate (api/query (api/opts (api/directives :foo :bar))
                                     (api/select :foo)))))
    (t/is (= "query @foo(bar:123){foo}"
             (unparse-and-validate (api/query (api/opts (api/directive :foo (api/arguments :bar 123)))
                                     (api/select :foo)))
             (unparse-and-validate (api/query (api/opts (api/directive :foo (api/argument :bar 123)))
                                     (api/select :foo)))))
    (t/is (= "{foo@bar}"
             (unparse-and-validate (api/select (api/field :foo (api/opts (api/directive :bar)))))
             (unparse-and-validate (api/select (api/field :foo (api/opts (api/directives :bar)))))))
    (t/is (= "{foo@bar qux@baz}"
             (unparse-and-validate (api/select
                                     (api/field :foo (api/opts (api/directive :bar)))
                                     (api/field :qux (api/opts (api/directive :baz)))))
             (unparse-and-validate (api/select
                                     (api/field :foo (api/opts (api/directives :bar)))
                                     (api/field :qux (api/opts (api/directives :baz)))))))
    (t/is (= "{foo@foo(bar:123)}"
             (unparse-and-validate (api/select
                                     (api/field :foo (api/opts (api/directive :foo {:bar 123})))))))
    (t/is (= "{...foo@fooDirective @barDirective}"
             (unparse-and-validate (api/select (api/fragment-spread (api/opts
                                                                     (api/name :foo)
                                                                     (api/directives :fooDirective :barDirective)))))
             (unparse-and-validate (api/select (api/fragment-spread (api/opts
                                                                     (api/name :foo)
                                                                     (api/directive :fooDirective)
                                                                     (api/directive :barDirective)))))))
    (t/is (= "{...foo@foo(bar:123)}"
             (unparse-and-validate (api/select
                                     (api/fragment-spread (api/opts
                                                           (api/name :foo)
                                                           (api/directive :foo (api/arguments :bar 123))))))
             (unparse-and-validate (api/select
                                     (api/fragment-spread (api/opts
                                                           (api/name :foo)
                                                           (api/directive :foo (api/argument :bar 123))))))))
    (t/is (= "{...@fooDirective @barDirective{bar}}"
             (unparse-and-validate (api/select
                                     (api/inline-fragment (api/opts
                                                           (api/directives :fooDirective :barDirective))
                                       (api/select :bar))))
             (unparse-and-validate (api/select
                                     (api/inline-fragment (api/opts
                                                           (api/directive :fooDirective)
                                                           (api/directive :barDirective))
                                       (api/select :bar))))))
    (t/is (= "{...@foo(bar:123){bar}}"
             (unparse-and-validate (api/select
                                     (api/inline-fragment (api/opts
                                                           (api/directive :foo (api/arguments :bar 123)))
                                       (api/select :bar))))
             (unparse-and-validate (api/select
                                     (api/inline-fragment (api/opts
                                                           (api/directive :foo (api/argument :bar 123)))
                                       (api/select :bar))))))
    (t/is (= "fragment foo on Foo@foo(bar:123){bar}"
             (unparse-and-validate (api/fragment (api/opts
                                                  (api/name :foo)
                                                  (api/on :Foo)
                                                  (api/directive :foo (api/argument :bar 123)))
                                     (api/select :bar)))
             (unparse-and-validate (api/fragment (api/opts
                                                  (api/name :foo)
                                                  (api/on :Foo)
                                                  (api/directive :foo (api/arguments :bar 123)))
                                     (api/select :bar)))))
    (t/is (= "fragment foo on Foo@fooDirective @barDirective{bar}"
             (unparse-and-validate (api/fragment (api/opts
                                                  (api/name :foo)
                                                  (api/on :Foo)
                                                  (api/directive :fooDirective)
                                                  (api/directive :barDirective))
                                     (api/select :bar)))
             (unparse-and-validate (api/fragment (api/opts
                                                  (api/name :foo)
                                                  (api/on :Foo)
                                                  (api/directives :fooDirective :barDirective))
                                     (api/select :bar)))))
    (t/is (= "query ($foo:Bar @fooDirective){fooField}"
             (unparse-and-validate (api/query (api/opts (api/variable :foo (api/opts (api/directives :fooDirective)) :Bar))
                                     (api/select :fooField)))
             (unparse-and-validate (api/query (api/opts (api/variable :foo (api/opts (api/directive :fooDirective)) :Bar))
                                     (api/select :fooField)))))
    (t/is (= "query ($foo:Bar @fooDirective @barDirective){fooField}"
             (unparse-and-validate (api/query (api/opts (api/variable :foo (api/opts (api/directives :fooDirective :barDirective)) :Bar))
                                     (api/select :fooField)))
             (unparse-and-validate (api/query (api/opts (api/variable :foo (api/opts (api/directive :fooDirective)
                                                                                     (api/directive :barDirective))
                                                          :Bar))
                                     (api/select :fooField)))))
    (t/is (= "query ($foo:Bar @fooDirective(fooArg:123)){fooField}"
             (unparse-and-validate (api/query (api/opts (api/variable :foo (api/opts (api/directive :fooDirective {:fooArg 123}))
                                                          :Bar))
                                     (api/select :fooField)))))))

(t/deftest transformers-test
  (t/testing "names are transformed when transformer fn is provided"
    (t/testing "selection set"
      (let [query (api/select
                    (api/field :foo-bar (api/opts
                                         (api/alias :bar-foo)
                                         (api/name-fn csk/->SCREAMING_SNAKE_CASE)
                                         (api/arguments :foo-arg :bar-value)
                                         (api/directives :foo-bar))
                      (api/select :foo-bar))
                    :naked-foo-bar
                    (api/inline-fragment
                      (api/select :foo-bar))
                    (api/inline-fragment (api/opts
                                          (api/on :foo-bar-fragment)
                                          (api/directives :foo-bar))
                      (api/select :foo-bar)))]
        (t/is (= "{BAR_FOO:FOO_BAR(FOO_ARG:BAR_VALUE)@FOO_BAR{FOO_BAR} nakedFooBar ...{fooBar} ...on fooBarFragment@fooBar{fooBar}}"
                 (unparse-and-validate {:oksa/name-fn csk/->camelCase} query)
                 (unparse-and-validate {:oksa/name-fn csk/->camelCase} (api/document query))))))
    (t/testing "query"
      (let [query (api/query
                   (api/opts (api/name :foo-bar-query)
                             (api/variable :foo-var (api/opts (api/default :foo-val)
                                                              (api/directive :foo-directive)) :foo-type)
                             (api/directives :foo-bar))
                   (api/select
                     (api/field :foo-bar (api/opts
                                          (api/alias :bar-foo)
                                          (api/arguments :foo-arg :bar-value)))
                     (api/field :foo-bar (api/opts (api/name-fn csk/->SCREAMING_SNAKE_CASE))
                       (api/select :foo-bar))
                     :naked-foo-bar
                     (api/inline-fragment
                       (api/select :foo-bar))
                     (api/inline-fragment (api/opts
                                           (api/on :foo-bar-fragment)
                                           (api/directives :foo-bar))
                       (api/select :foo-bar))))]
        (t/is (= "query fooBarQuery ($fooVar:fooType=fooVal @fooDirective)@fooBar{barFoo:fooBar(fooArg:barValue) FOO_BAR{FOO_BAR} nakedFooBar ...{fooBar} ...on fooBarFragment@fooBar{fooBar}}"
                 (unparse-and-validate {:oksa/name-fn csk/->camelCase} query)
                 (unparse-and-validate {:oksa/name-fn csk/->camelCase} (api/document query))))))
    (t/testing "mutation"
      (let [query (api/mutation
                   (api/opts (api/name :foo-bar-query)
                             (api/variable :foo-var (api/opts (api/default :foo-val)
                                                              (api/directive :foo-directive)) :foo-type)
                             (api/directives :foo-bar))
                   (api/select
                     (api/field :foo-bar (api/opts
                                          (api/alias :bar-foo)
                                          (api/arguments :foo-arg :bar-value)))
                     (api/field :foo-bar (api/opts (api/name-fn csk/->SCREAMING_SNAKE_CASE))
                       (api/select :foo-bar))
                     :naked-foo-bar
                     (api/inline-fragment
                       (api/select :foo-bar))
                     (api/inline-fragment (api/opts
                                           (api/on :foo-bar-fragment)
                                           (api/directives :foo-bar))
                       (api/select :foo-bar))))]
        (t/is (= "mutation fooBarQuery ($fooVar:fooType=fooVal @fooDirective)@fooBar{barFoo:fooBar(fooArg:barValue) FOO_BAR{FOO_BAR} nakedFooBar ...{fooBar} ...on fooBarFragment@fooBar{fooBar}}"
                 (unparse-and-validate {:oksa/name-fn csk/->camelCase} query)
                 (unparse-and-validate {:oksa/name-fn csk/->camelCase} (api/document query))))))
    (t/testing "subscription"
      (let [query (api/subscription
                   (api/opts (api/name :foo-bar-query)
                             (api/variable :foo-var (api/opts (api/default :foo-val)
                                                              (api/directive :foo-directive)) :foo-type)
                             (api/directives :foo-bar))
                   (api/select
                     (api/field :foo-bar (api/opts
                                          (api/alias :bar-foo)
                                          (api/arguments :foo-arg :bar-value)))
                     (api/field :foo-bar (api/opts (api/name-fn csk/->SCREAMING_SNAKE_CASE))
                       (api/select :foo-bar))
                     :naked-foo-bar
                     (api/inline-fragment
                       (api/select :foo-bar))
                     (api/inline-fragment (api/opts
                                           (api/on :foo-bar-fragment)
                                           (api/directives :foo-bar))
                       (api/select :foo-bar))))]
        (t/is (= "subscription fooBarQuery ($fooVar:fooType=fooVal @fooDirective)@fooBar{barFoo:fooBar(fooArg:barValue) FOO_BAR{FOO_BAR} nakedFooBar ...{fooBar} ...on fooBarFragment@fooBar{fooBar}}"
                 (unparse-and-validate {:oksa/name-fn csk/->camelCase} query)
                 (unparse-and-validate {:oksa/name-fn csk/->camelCase} (api/document query))))))
    (t/testing "fragment"
      (let [query (api/fragment (api/opts
                                 (api/name :foo-bar-fragment)
                                 (api/on :foo-bar-type)
                                 (api/directives :foo-bar))
                                (api/select
                                  (api/field :foo-bar (api/opts
                                                       (api/alias :bar-foo)
                                                       (api/arguments :foo-arg :bar-value)))
                                  (api/field :foo-bar (api/opts (api/name-fn csk/->SCREAMING_SNAKE_CASE))
                                    (api/select :foo-bar))
                                  :naked-foo-bar
                                  (api/inline-fragment
                                    (api/select :foo-bar))
                                  (api/inline-fragment (api/opts
                                                        (api/on :foo-bar-fragment)
                                                        (api/directives :foo-bar))
                                    (api/select :foo-bar))))]
        (t/is (= "fragment fooBarFragment on fooBarType@fooBar{barFoo:fooBar(fooArg:barValue) FOO_BAR{FOO_BAR} nakedFooBar ...{fooBar} ...on fooBarFragment@fooBar{fooBar}}"
                 (unparse-and-validate {:oksa/name-fn csk/->camelCase} query)
                 (unparse-and-validate {:oksa/name-fn csk/->camelCase} (api/document query))))))))
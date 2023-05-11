# oksa

Generate GraphQL queries using Clojure data structures.

- Support latest stable GraphQL spec, [ExecutableDefinitions](https://spec.graphql.org/October2021/#ExecutableDefinition) only
- [Malli](https://github.com/metosin/malli)-like syntax
- clojure + clojurescript

## Installation

TODO: clojars release

Oksa requires Clojure 1.10+.

## Usage

```clojure
(require '[oksa.core :as oksa])
(oksa/unparse [:hello [:world]])
;; => "{hello{world}}"
```

### Operation definitions

#### Selection sets

Fields can be selected:

```clojure
(core/unparse [:foo])
;; => "{foo}"

(core/unparse [:foo :bar])
;; => "{foo bar}"

(core/unparse [:bar [:qux [:baz]]])
;; => "{bar{qux{baz}}}"

(core/unparse [:foo :bar [:qux [:baz]]])
;; => "{foo bar{qux{baz}}}"

(core/unparse [:foo :bar [:qux :baz]])
;; => "{foo bar{qux baz}}"

(core/unparse [:foo [:bar [:baz :qux] :frob]])
;; => "{foo{bar{baz qux} frob}}"
```

Strings are supported for field names:

```clojure
(core/unparse ["query" "foo"])
;; => "{query foo}"
```

Aliases:

```clojure
(core/unparse [[:foo {:alias :bar}]])
;; => "{bar:foo}"
```

Arguments:

```clojure
(core/unparse [[:foo {:arguments {:a 1
                                  :b "hello world"
                                  :c true
                                  :d nil
                                  :e :foo
                                  :f [1 2 3]
                                  :g {:frob {:foo 1
                                             :bar 2}}
                                  :h :$fooVar}}]])
;; => "{foo(a:1, b:\"hello world\", c:true, d:null, e:foo, f:[1 2 3], g:{frob:{foo:1, bar:2}}, h:$fooVar)}"
```

Directives:

```clojure
(core/unparse [[:foo {:directives [:bar]}]])
;; => "{foo@bar}"

;; with arguments
(core/unparse [[:foo {:directives [[:bar {:arguments {:qux 123}}]]}]])
;; => "{foo@bar(qux:123)}"
```

#### Queries

Queries can be created:

```clojure
(core/unparse [:query [:foo :bar [:qux [:baz]]]])
;; => "query {foo bar{qux{baz}}}"

(core/unparse [:query {:name :Foo} [:foo]])
;; => "query Foo {foo}"
```

Queries can have directives:

```clojure
(core/unparse [:query {:directives [:foo]} [:foo]])
;; => "query @foo{foo}"

(core/unparse [:query {:directives [:foo :bar]} [:foo]])
;; => "query @foo @bar{foo}"

;; with arguments

(core/unparse [:query {:directives [[:foo {:arguments {:bar 123}}]]} [:foo]])
;; => "query @foo(bar:123){foo}"
```

#### Mutations

Mutations can be created:

```clojure
(core/unparse [:mutation [:foo :bar [:qux [:baz]]]])
;; => "mutation {foo bar{qux{baz}}}"

(core/unparse [:mutation {:name :Foo} [:foo]])
;; => "mutation Foo {foo}"
```

#### Subscriptions

Subscriptions can be created:

```clojure
(core/unparse [:subscription [:foo :bar [:qux [:baz]]]])
;; => "subscription {foo bar{qux{baz}}}"

(core/unparse [:subscription {:name :Foo} [:foo]])
;; => "subscription Foo {foo}"
```

#### Variable definitions

Named types are supported:

```clojure
(core/unparse [:query {:variables [:fooVar :FooType]}
               [:fooField]])
;; => "query ($fooVar:FooType){fooField}"

(core/unparse [:query {:variables
                       [:fooVar :FooType
                        :barVar :BarType]}
               [:fooField]])
;; => "query ($fooVar:FooType,$barVar:BarType){fooField}"
```

Lists can be created:

```clojure
(core/unparse [:query {:variables
                       [:fooVar [:oksa/list :FooType]]}
               [:fooField]])

;; or

(core/unparse [:query {:variables
                       [:fooVar [:FooType]]}
               [:fooField]])
;; => "query ($fooVar:[FooType]){fooField}"

(core/unparse [:query {:variables
                       [:fooVar [:oksa/list
                                 [:oksa/list
                                  :BarType]]]}
               [:fooField]])

;; or

(core/unparse [:query {:variables [:fooVar [[:BarType]]]}
               [:fooField]])
;; => "query ($fooVar:[[BarType]]){fooField}"
```

Non-null types can be created:

```clojure
(core/unparse [:query {:variables
                       [:fooVar [:FooType {:oksa/non-null? true}]]}
               [:fooField]])

;; or

(core/unparse [:query {:variables [:fooVar :FooType!]}
               [:fooField]])
;; => "query ($fooVar:FooType!){fooField}"

(core/unparse [:query {:variables
                       [:fooVar [:oksa/list {:oksa/non-null? true}
                                 :BarType]]}
               [:fooField]])

;; or

(core/unparse [:query {:variables [:fooVar [:! :BarType]]}
               [:fooField]])
;; => "query ($fooVar:[BarType]!){fooField}"
```

Getting crazy with it:

```clojure
(core/unparse [:query {:variables [:fooVar [:! [:! :BarType!]]]}
               [:fooField]])
;; => "query ($fooVar:[[BarType!]!]!){fooField}"
```

Variable definitions can have directives:

```clojure
(core/unparse [:query {:variables [:foo {:directives [:fooDirective]} :Bar]}
               [:fooField]])
;; => "query ($foo:Bar @fooDirective){fooField}"

(core/unparse [:query {:variables [:foo {:directives [[:fooDirective {:arguments {:fooArg 123}}]]} :Bar]}
               [:fooField]])
;; => "query ($foo:Bar @fooDirective(fooArg:123)){fooField}"
```

### Fragments

Fragment definitions can be created:

```clojure
(core/unparse [:fragment {:name :Foo :on :Bar} [:foo]])
;; => "fragment Foo on Bar{foo}"

(core/unparse [:# {:name :Foo :on :Bar} [:foo]]) ; shortcut
;; => "fragment Foo on Bar{foo}"

;; with directives
(core/unparse [:fragment {:name :foo
                          :on :Foo
                          :directives [:fooDirective]}
               [:bar]])
;; => "fragment foo on Foo@fooDirective{bar}"

;; with arguments
(core/unparse [:fragment {:name :foo
                          :on :Foo
                          :directives [[:fooDirective {:arguments {:bar 123}}]]}
               [:bar]])
;; => "fragment foo on Foo@fooDirective(bar:123){bar}"
```

Fragment spreads:

```clojure
(core/unparse [:foo [:fragment-spread {:name :bar}]])

;; or

(core/unparse [:foo [:... {:name :bar}]])
;; => "{foo ...bar}"

;; with directives
(core/unparse [[:... {:name :foo :directives [:bar]}]])
;; => "{...foo@bar}"

;; with arguments
(core/unparse [[:... {:name :foo
                      :directives [[:bar {:arguments {:qux 123}}]]}]])
;; => "{...foo@bar(qux:123)}"
```

Inline fragments:

```clojure
(core/unparse [:foo [:inline-fragment [:bar]]])

;; or

(core/unparse [:foo [:... [:bar]]])
;; => "{foo ...{bar}}"

(core/unparse [:foo [:... {:on :Bar} [:bar]]])
;; => "{foo ...on Bar{bar}}"

;; with directives
(core/unparse [[:... {:directives [:foo]} [:bar]]])
;; => "{...@foo{bar}}"

;; with arguments
(core/unparse [[:... {:directives [[:foo {:arguments {:bar 123}}]]}
                [:foobar]]])
;; => "{...@foo(bar:123){foobar}}"
```

### Document

Putting it all together:

```clojure
(core/unparse [:document
               [:foo]
               [:query [:bar]]
               [:mutation [:qux]]
               [:subscription [:baz]]
               [:fragment {:name :foo :on :Foo} [:bar]]])
;; => "{foo}\nquery {bar}\nmutation {qux}\nsubscription {baz}\nfragment foo on Foo{bar}"

(core/unparse [:<> [:foo] [:bar]]) ; :<> also supported
;; => "{foo}\n{bar}"
```

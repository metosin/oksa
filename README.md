# oksa

- [Project status](#project-status)
- [Installation](#installation)
- [Usage](#usage)
  - [Selection sets](#selection-sets)
  - [Queries](#queries)
  - [Mutations](#mutations)
  - [Subscriptions](#subscriptions)
  - [Variable definitions](#variable-definitions)
  - [Fragments](#fragments)
  - [Document](#document)
- [Rationale](#rationale)

Generate GraphQL queries using Clojure data structures.

- Support latest stable [GraphQL spec](https://spec.graphql.org/October2021)
- [Malli](https://github.com/metosin/malli)-like syntax
- clojure + clojurescript

## Project status

[![Slack](https://img.shields.io/badge/slack-metosin-orange.svg?logo=slack)](https://clojurians.slack.com/app_redirect?channel=metosin)

Oksa is currently [experimental](https://github.com/topics/metosin-experimental).

## Installation

Using deps.edn:

```clojure
{:deps {metosin/oksa {:git/url "https://github.com/metosin/oksa"
                      :sha "6ea4a246a14e1eb2f1988774f270624d1bd309fe"}}}
```

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
(oksa/unparse [:foo])
;; => "{foo}"

(oksa/unparse [:foo :bar])
;; => "{foo bar}"

(oksa/unparse [:bar [:qux [:baz]]])
;; => "{bar{qux{baz}}}"

(oksa/unparse [:foo :bar [:qux [:baz]]])
;; => "{foo bar{qux{baz}}}"

(oksa/unparse [:foo :bar [:qux :baz]])
;; => "{foo bar{qux baz}}"

(oksa/unparse [:foo [:bar [:baz :qux] :frob]])
;; => "{foo{bar{baz qux} frob}}"
```

Strings are supported for field names:

```clojure
(oksa/unparse ["query" "foo"])
;; => "{query foo}"
```

Aliases:

```clojure
(oksa/unparse [[:foo {:alias :bar}]])
;; => "{bar:foo}"
```

Arguments:

```clojure
(oksa/unparse [[:foo {:arguments {:a 1
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
(oksa/unparse [[:foo {:directives [:bar]}]])
;; => "{foo@bar}"

;; with arguments
(oksa/unparse [[:foo {:directives [[:bar {:arguments {:qux 123}}]]}]])
;; => "{foo@bar(qux:123)}"
```

#### Queries

Queries can be created:

```clojure
(oksa/unparse [:oksa/query [:foo :bar [:qux [:baz]]]])
;; => "query {foo bar{qux{baz}}}"

(oksa/unparse [:oksa/query {:name :Foo} [:foo]])
;; => "query Foo {foo}"
```

Queries can have directives:

```clojure
(oksa/unparse [:oksa/query {:directives [:foo]} [:foo]])
;; => "query @foo{foo}"

(oksa/unparse [:oksa/query {:directives [:foo :bar]} [:foo]])
;; => "query @foo @bar{foo}"

;; with arguments

(oksa/unparse [:oksa/query {:directives [[:foo {:arguments {:bar 123}}]]} [:foo]])
;; => "query @foo(bar:123){foo}"
```

#### Mutations

Mutations can be created:

```clojure
(oksa/unparse [:oksa/mutation [:foo :bar [:qux [:baz]]]])
;; => "mutation {foo bar{qux{baz}}}"

(oksa/unparse [:oksa/mutation {:name :Foo} [:foo]])
;; => "mutation Foo {foo}"
```

#### Subscriptions

Subscriptions can be created:

```clojure
(oksa/unparse [:oksa/subscription [:foo :bar [:qux [:baz]]]])
;; => "subscription {foo bar{qux{baz}}}"

(oksa/unparse [:oksa/subscription {:name :Foo} [:foo]])
;; => "subscription Foo {foo}"
```

#### Variable definitions

Named types are supported:

```clojure
(oksa/unparse [:oksa/query {:variables [:fooVar :FooType]}
               [:fooField]])
;; => "query ($fooVar:FooType){fooField}"

(oksa/unparse [:oksa/query {:variables
                            [:fooVar :FooType
                             :barVar :BarType]}
               [:fooField]])
;; => "query ($fooVar:FooType,$barVar:BarType){fooField}"
```

Lists can be created:

```clojure
(oksa/unparse [:oksa/query {:variables
                            [:fooVar [:oksa/list :FooType]]}
               [:fooField]])

;; or

(oksa/unparse [:oksa/query {:variables
                            [:fooVar [:FooType]]}
               [:fooField]])
;; => "query ($fooVar:[FooType]){fooField}"

(oksa/unparse [:oksa/query {:variables
                            [:fooVar [:oksa/list
                                      [:oksa/list
                                       :BarType]]]}
               [:fooField]])

;; or

(oksa/unparse [:oksa/query {:variables [:fooVar [[:BarType]]]}
               [:fooField]])
;; => "query ($fooVar:[[BarType]]){fooField}"
```

Non-null types can be created:

```clojure
(oksa/unparse [:oksa/query {:variables
                            [:fooVar [:FooType {:non-null true}]]}
               [:fooField]])

;; or

(oksa/unparse [:oksa/query {:variables [:fooVar :FooType!]}
               [:fooField]])
;; => "query ($fooVar:FooType!){fooField}"

(oksa/unparse [:oksa/query {:variables
                            [:fooVar [:oksa/list {:non-null true}
                                      :BarType]]}
               [:fooField]])

;; or

(oksa/unparse [:oksa/query {:variables [:fooVar [:! :BarType]]}
               [:fooField]])
;; => "query ($fooVar:[BarType]!){fooField}"
```

Getting crazy with it:

```clojure
(oksa/unparse [:oksa/query {:variables [:fooVar [:! [:! :BarType!]]]}
               [:fooField]])
;; => "query ($fooVar:[[BarType!]!]!){fooField}"
```

Variable definitions can have directives:

```clojure
(oksa/unparse [:oksa/query {:variables [:foo {:directives [:fooDirective]} :Bar]}
               [:fooField]])
;; => "query ($foo:Bar @fooDirective){fooField}"

(oksa/unparse [:oksa/query {:variables [:foo {:directives [[:fooDirective {:arguments {:fooArg 123}}]]} :Bar]}
               [:fooField]])
;; => "query ($foo:Bar @fooDirective(fooArg:123)){fooField}"
```

### Fragments

Fragment definitions can be created:

```clojure
(oksa/unparse [:oksa/fragment {:name :Foo :on :Bar} [:foo]])
;; => "fragment Foo on Bar{foo}"

(oksa/unparse [:# {:name :Foo :on :Bar} [:foo]]) ; shortcut
;; => "fragment Foo on Bar{foo}"

;; with directives
(oksa/unparse [:oksa/fragment {:name :foo
                               :on :Foo
                               :directives [:fooDirective]}
               [:bar]])
;; => "fragment foo on Foo@fooDirective{bar}"

;; with arguments
(oksa/unparse [:oksa/fragment {:name :foo
                               :on :Foo
                               :directives [[:fooDirective {:arguments {:bar 123}}]]}
               [:bar]])
;; => "fragment foo on Foo@fooDirective(bar:123){bar}"
```

Fragment spreads:

```clojure
(oksa/unparse [:foo [:oksa/fragment-spread {:name :bar}]])

;; or

(oksa/unparse [:foo [:... {:name :bar}]])
;; => "{foo ...bar}"

;; with directives
(oksa/unparse [[:... {:name :foo :directives [:bar]}]])
;; => "{...foo@bar}"

;; with arguments
(oksa/unparse [[:... {:name :foo
                      :directives [[:bar {:arguments {:qux 123}}]]}]])
;; => "{...foo@bar(qux:123)}"
```

Inline fragments:

```clojure
(oksa/unparse [:foo [:oksa/inline-fragment [:bar]]])

;; or

(oksa/unparse [:foo [:... [:bar]]])
;; => "{foo ...{bar}}"

(oksa/unparse [:foo [:... {:on :Bar} [:bar]]])
;; => "{foo ...on Bar{bar}}"

;; with directives
(oksa/unparse [[:... {:directives [:foo]} [:bar]]])
;; => "{...@foo{bar}}"

;; with arguments
(oksa/unparse [[:... {:directives [[:foo {:arguments {:bar 123}}]]}
                [:foobar]]])
;; => "{...@foo(bar:123){foobar}}"
```

### Document

Putting it all together:

```clojure
(oksa/unparse [:oksa/document
               [:foo]
               [:oksa/query [:bar]]
               [:oksa/mutation [:qux]]
               [:oksa/subscription [:baz]]
               [:oksa/fragment {:name :foo :on :Foo} [:bar]]])
;; => "{foo}\nquery {bar}\nmutation {qux}\nsubscription {baz}\nfragment foo on Foo{bar}"

(oksa/unparse [:<> [:foo] [:bar]]) ; :<> also supported
;; => "{foo}\n{bar}"
```

## Rationale

There are some awesome GraphQL query generation libraries out there, notably:

* https://github.com/oliyh/re-graph
  * Handles queries, subscriptions and mutations over HTTP and WebSocket.
  * Combines many things; not a pure query generation libary.
* https://github.com/retro/graphql-builder
  * A HugSQL-like library for GraphQL query generation.
* https://github.com/Vincit/venia
  * Generates GraphQL queries with Clojure data structures.
  * See also the more recently updated fork: https://github.com/district0x/graphql-query

With oksa we want to provide:
* A platform-agnostic library meant for purely building GraphQL queries.
* Support for the entire syntax under [ExecutableDefinition](https://spec.graphql.org/October2021/#ExecutableDefinition) plus some parts from [Document](https://spec.graphql.org/October2021/#Document) for added composability of queries.
* A data-driven library with a [malli](https://github.com/metosin/malli)-like syntax.

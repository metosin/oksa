# oksa

- [Project status](#project-status)
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
- [Malli](https://github.com/metosin/malli)-like syntax or programmatic API
- clojure + clojurescript

## Project status

[![Clojars Project](https://img.shields.io/clojars/v/fi.metosin/oksa.svg)](https://clojars.org/fi.metosin/oksa)
[![Slack](https://img.shields.io/badge/slack-metosin-orange.svg?logo=slack)](https://clojurians.slack.com/app_redirect?channel=metosin)
[![cljdoc badge](https://cljdoc.org/badge/fi.metosin/oksa)](https://cljdoc.org/d/fi.metosin/oksa)

Oksa is currently [experimental](https://github.com/topics/metosin-experimental).

## Usage

```clojure
(require '[oksa.core :as oksa])
(require '[oksa.alpha.api :refer :all])

(oksa/gql [:hello [:world]])
;; => "{hello{world}}"

(oksa/gql (select :hello (select :world)))
```

More complete example using `oksa.alpha.api`:

```clojure
(oksa/gql
  (document
    (fragment (opts
                (name :Kikka)
                (on :Kukka))
      (select :kikka :kukka))
    (select :ylakikka
      (select :kikka :kukka :kakku)
      ;; v similar to ^, but allow to specify option for single field (only)
      (field :kikka (opts
                      (alias :KIKKA)
                      (directives :kakku :kukka)
                      (directive :kikkaized {:x 1 :y 2 :z 3})))
      (when false (field :conditionalKikka)) ; nils are dropped
      (fragment-spread (opts (name :FooFragment)))
      (inline-fragment (opts (on :Kikka))
        (select :kikka :kukka)))
    (query (opts (name :KikkaQuery))
      (select :specialKikka))
    (mutation (opts
                (name :saveKikka)
                (variable :myKikka (opts (default 123)) :KikkaType))
      (select :getKikka))
    (subscription (opts (name :subscribeToKikka))
      (select :realtimeKikka))))

; =>
; "fragment Kikka on Kukka{kikka kukka}
;  {ylakikka{kikka kukka kakku} KIKKA:kikka@kakku @kukka @kikkaized(x:1, y:2, z:3) ...FooFragment ...on Kikka{kikka kukka}}
;  query KikkaQuery {specialKikka}
;  mutation saveKikka ($myKikka:KikkaType=123){getKikka}
;  subscription subscribeToKikka {realtimeKikka}"
```

### Operation definitions

#### Selection sets

Fields can be selected:

```clojure
(oksa/gql [:foo])
(oksa/gql (select :foo))
;; => "{foo}"

(oksa/gql [:foo :bar])
(oksa/gql (select :foo :bar))
;; => "{foo bar}"

(oksa/gql [:bar [:qux [:baz]]])
(oksa/gql (select
            :bar
            (select
              :qux
              (select :baz))))
;; => "{bar{qux{baz}}}"

(oksa/gql [:foo :bar [:qux [:baz]]])
(oksa/gql (select
            :foo
            :bar
            (select
              :qux
              (select :baz))))
;; => "{foo bar{qux{baz}}}"

(oksa/gql [:foo :bar [:qux :baz]])
(oksa/gql (select
            :foo
            :bar
            (select :qux :baz)))
;; => "{foo bar{qux baz}}"

(oksa/gql [:foo [:bar [:baz :qux] :frob]])
(oksa/gql (select
            :foo
            (select
              :bar
              (select :baz :qux)
              :frob)))
;; => "{foo{bar{baz qux} frob}}"
```

Strings are supported for field names:

```clojure
(oksa/gql ["query" "foo"])
(oksa/gql (select "query" "foo"))
;; => "{query foo}"
```

Aliases:

```clojure
(oksa/gql [[:foo {:alias :bar}]])
(oksa/gql (select (field :foo (opts (alias :bar)))))
;; => "{bar:foo}"
```

Arguments:

```clojure
(oksa/gql [[:foo {:arguments {:a 1
                              :b "hello world"
                              :c true
                              :d nil
                              :e :foo
                              :f [1 2 3]
                              :g {:frob {:foo 1
                                         :bar 2}}
                              :h :$fooVar}}]])
;; => "{foo(a:1, b:\"hello world\", c:true, d:null, e:foo, f:[1 2 3], g:{frob:{foo:1, bar:2}}, h:$fooVar)}"

;; alt
(oksa/gql (select (field :foo (opts (arguments
                                      :a 1
                                      :b "hello world"
                                      :c true
                                      :d nil
                                      :e :foo
                                      :f [1 2 3]
                                      :g {:frob {:foo 1
                                                 :bar 2}}
                                      :h :$fooVar)))))
```

Directives:

```clojure
(oksa/gql [[:foo {:directives [:bar]}]])
(oksa/gql (select (field :foo (opts (directive :bar)))))
;; => "{foo@bar}"

;; with arguments
(oksa/gql [[:foo {:directives [[:bar {:arguments {:qux 123}}]]}]])
(oksa/gql (select (field :foo (opts (directive :bar (arguments :qux 123))))))
;; => "{foo@bar(qux:123)}"
```

#### Queries

Queries can be created:

```clojure
(oksa/gql [:oksa/query [:foo :bar [:qux [:baz]]]])
(oksa/gql (query
            (select
              :foo
              :bar
              (select
                :qux
                (select :baz)))))
;; => "query {foo bar{qux{baz}}}"

(oksa/gql [:oksa/query {:name :Foo} [:foo]])
(oksa/gql (query (opts (name :Foo)) (select :foo)))
;; => "query Foo {foo}"
```

Queries can have directives:

```clojure
(oksa/gql [:oksa/query {:directives [:foo]} [:foo]])
(oksa/gql (query (opts (directive :foo)) (select :foo)))
;; => "query @foo{foo}"

(oksa/gql [:oksa/query {:directives [:foo :bar]} [:foo]])
(oksa/gql (query (opts (directives :foo :bar)) (select :foo)))
;; => "query @foo @bar{foo}"

;; with arguments

(oksa/gql [:oksa/query {:directives [[:foo {:arguments {:bar 123}}]]} [:foo]])
(oksa/gql (query (opts (directive :foo (arguments :bar 123))) (select :foo)))
;; => "query @foo(bar:123){foo}"
```

#### Mutations

Mutations can be created:

```clojure
(oksa/gql [:oksa/mutation [:foo :bar [:qux [:baz]]]])
(oksa/gql (mutation (select
                      :foo
                      :bar
                      (select
                        :qux
                        (select :baz)))))
;; => "mutation {foo bar{qux{baz}}}"

(oksa/gql [:oksa/mutation {:name :Foo} [:foo]])
(oksa/gql (mutation (opts (name :Foo)) (select :foo)))
;; => "mutation Foo {foo}"
```

#### Subscriptions

Subscriptions can be created:

```clojure
(oksa/gql [:oksa/subscription [:foo :bar [:qux [:baz]]]])
(oksa/gql (subscription (select
                          :foo
                          :bar
                          (select
                            :qux
                            (select :baz)))))
;; => "subscription {foo bar{qux{baz}}}"

(oksa/gql [:oksa/subscription {:name :Foo} [:foo]])
(oksa/gql (subscription (opts (name :Foo))
            (select :foo)))
;; => "subscription Foo {foo}"
```

#### Variable definitions

Named types are supported:

```clojure
(oksa/gql [:oksa/query {:variables [:fooVar :FooType]} [:fooField]])
(oksa/gql (query (opts (variables :fooVar :FooType))
            (select :fooField)))
;; => "query ($fooVar:FooType){fooField}"

(oksa/gql [:oksa/query {:variables [:fooVar :FooType :barVar :BarType]} [:fooField]])
(oksa/gql (query (opts (variables :fooVar :FooType :barVar :BarType))
            (select :fooField)))
;; => "query ($fooVar:FooType,$barVar:BarType){fooField}"
```

Lists can be created:

```clojure
(oksa/gql [:oksa/query {:variables [:fooVar [:oksa/list :FooType]]} [:fooField]])
(oksa/gql [:oksa/query {:variables [:fooVar [:FooType]]} [:fooField]])
(oksa/gql (query (opts (variable :fooVar (list :FooType)))
            (select :fooField)))
;; => "query ($fooVar:[FooType]){fooField}"

(oksa/gql [:oksa/query {:variables [:fooVar [:oksa/list [:oksa/list :BarType]]]} [:fooField]])
(oksa/gql [:oksa/query {:variables [:fooVar [[:BarType]]]} [:fooField]])
(oksa/gql (query (opts (variable :fooVar (list (list :BarType))))
            (select :fooField)))
;; => "query ($fooVar:[[BarType]]){fooField}"
```

Non-null types can be created:

```clojure
(oksa/gql [:oksa/query {:variables [:fooVar [:FooType {:non-null true}]]} [:fooField]])
(oksa/gql [:oksa/query {:variables [:fooVar :FooType!]} [:fooField]])
(oksa/gql (query (opts (variable :fooVar (type! :FooType)))
            (select :fooField)))
;; => "query ($fooVar:FooType!){fooField}"

(oksa/gql [:oksa/query {:variables [:fooVar [:oksa/list {:non-null true} :BarType]]} [:fooField]])
(oksa/gql [:oksa/query {:variables [:fooVar [:! :BarType]]} [:fooField]])
(oksa/gql (query (opts (variable :fooVar (list! :BarType)))
            (select :fooField)))
;; => "query ($fooVar:[BarType]!){fooField}"
```

Getting crazy with it:

```clojure
(oksa/gql [:oksa/query {:variables [:fooVar [:! [:! :BarType!]]]} [:fooField]])
(oksa/gql (query (opts (variable :fooVar (list! (list! (type! :BarType)))))
            (select :fooField)))
;; => "query ($fooVar:[[BarType!]!]!){fooField}"
```

Variable definitions can have directives:

```clojure
(oksa/gql [:oksa/query {:variables [:foo {:directives [:fooDirective]} :Bar]} [:fooField]])
(oksa/gql (query (opts (variable :foo (opts (directive :fooDirective)) :Bar))
            (select :fooField)))
;; => "query ($foo:Bar @fooDirective){fooField}"

(oksa/gql [:oksa/query {:variables [:foo {:directives [[:fooDirective {:arguments {:fooArg 123}}]]} :Bar]} [:fooField]])
(oksa/gql (query (opts (variable :foo (opts (directive :fooDirective (argument :fooArg 123))) :Bar))
            (select :fooField)))
;; => "query ($foo:Bar @fooDirective(fooArg:123)){fooField}"
```

### Fragments

Fragment definitions can be created:

```clojure
(oksa/gql [:oksa/fragment {:name :Foo :on :Bar} [:foo]])
(oksa/gql [:# {:name :Foo :on :Bar} [:foo]]) ; shortcut
(oksa/gql (fragment (opts
                      (name :Foo)
                      (on :Bar))
            (select :foo)))
;; => "fragment Foo on Bar{foo}"

;; with directives
(oksa/gql [:oksa/fragment {:name :foo
                           :on :Foo
                           :directives [:fooDirective]}
           [:bar]])
; alt
(oksa/gql (fragment (opts
                      (name :foo)
                      (on :Foo)
                      (directive :fooDirective))
            (select :bar)))
;; => "fragment foo on Foo@fooDirective{bar}"

;; with arguments
(oksa/gql [:oksa/fragment {:name :foo
                           :on :Foo
                           :directives [[:fooDirective {:arguments {:bar 123}}]]} [:bar]])
; alt
(oksa/gql (fragment (opts
                      (name :foo)
                      (on :Foo)
                      (directive :fooDirective (argument :bar 123)))
            (select :bar)))
;; => "fragment foo on Foo@fooDirective(bar:123){bar}"
```

Fragment spreads:

```clojure
(oksa/gql [:foo [:oksa/fragment-spread {:name :bar}]])
(oksa/gql [:foo [:... {:name :bar}]])
(oksa/gql (select :foo (fragment-spread (opts (name :bar)))))
;; => "{foo ...bar}"

;; with directives
(oksa/gql [[:... {:name :foo :directives [:bar]}]])
(oksa/gql (select (fragment-spread (opts (name :foo) (directive :bar)))))
;; => "{...foo@bar}"

;; with arguments
(oksa/gql [[:... {:name :foo :directives [[:bar {:arguments {:qux 123}}]]}]])
(oksa/gql (select (fragment-spread (opts (name :foo) (directive :bar (arguments :qux 123))))))
;; => "{...foo@bar(qux:123)}"
```

Inline fragments:

```clojure
(oksa/gql [:foo [:oksa/inline-fragment [:bar]]])
(oksa/gql [:foo [:... [:bar]]])
(oksa/gql (select :foo (inline-fragment
                         (select :bar))))
;; => "{foo ...{bar}}"

(oksa/gql [:foo [:... {:on :Bar} [:bar]]])
(oksa/gql (select :foo (inline-fragment (opts (on :Bar))
                         (select :bar))))
;; => "{foo ...on Bar{bar}}"

;; with directives
(oksa/gql [[:... {:directives [:foo]} [:bar]]])
(oksa/gql (select (inline-fragment (opts (directive :foo))
                    (select :bar))))
;; => "{...@foo{bar}}"

;; with arguments
(oksa/gql [[:... {:directives [[:foo {:arguments {:bar 123}}]]} [:foobar]]])
(oksa/gql (select (inline-fragment (opts (directive :foo (argument :bar 123)))
                    (select :foobar))))
;; => "{...@foo(bar:123){foobar}}"
```

### Document

Putting it all together:

```clojure
(oksa/gql [:oksa/document
           [:foo]
           [:oksa/query [:bar]]
           [:oksa/mutation [:qux]]
           [:oksa/subscription [:baz]]
           [:oksa/fragment {:name :foo :on :Foo} [:bar]]])
;; => "{foo}\nquery {bar}\nmutation {qux}\nsubscription {baz}\nfragment foo on Foo{bar}"

;; alt
(oksa/gql
  (document
    (select :foo)
    (query (select :bar))
    (mutation (select :qux))
    (subscription (select :baz))
    (fragment (opts
                (name :foo)
                (on :Foo))
      (select :bar))))

(oksa/gql [:<> [:foo] [:bar]]) ; :<> also supported
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

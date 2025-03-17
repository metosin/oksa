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
  - [Name transformation](#name-transformation)
- [Rationale](#rationale)

Generate GraphQL queries using Clojure data structures.

- Support latest stable [GraphQL spec](https://spec.graphql.org/October2021)
- [Malli](https://github.com/metosin/malli)-like syntax or programmatic API
- Clojure, ClojureScript, and babashka

## Project status

[![Clojars Project](https://img.shields.io/clojars/v/fi.metosin/oksa.svg)](https://clojars.org/fi.metosin/oksa)
[![Slack](https://img.shields.io/badge/slack-oksa-orange.svg?logo=slack)](https://clojurians.slack.com/app_redirect?channel=oksa)
[![cljdoc badge](https://cljdoc.org/badge/fi.metosin/oksa)](https://cljdoc.org/d/fi.metosin/oksa)
[![bb compatible](https://raw.githubusercontent.com/babashka/babashka/master/logo/badge.svg)](https://book.babashka.org#badges)

Oksa is currently [experimental](https://github.com/topics/metosin-experimental).

## Usage

```clojure
(require '[oksa.core :as o])
(require '[oksa.alpha.api :as oa])

(o/gql [:hello [:world]])
;; => "{hello{world}}"

;; programmatic
(o/gql (oa/select :hello (oa/select :world)))
;; => "{hello{world}}"
```

### Operation definitions

#### Selection sets

Fields can be selected:

```clojure
(o/gql [:foo])
;; => "{foo}"

(o/gql (oa/select :foo))
;; => "{foo}"
```

```clojure
(o/gql [:foo :bar])
;; => "{foo bar}"

(o/gql (oa/select :foo :bar))
;; => "{foo bar}"
```

```clojure
(o/gql [:bar [:qux [:baz]]])
;; => "{bar{qux{baz}}}"

(o/gql (oa/select :bar
         (oa/select :qux
           (oa/select :baz))))
;; => "{bar{qux{baz}}}"
```

```clojure
(o/gql [:foo :bar [:qux [:baz]]])
;; => "{foo bar{qux{baz}}}"

(o/gql (oa/select :foo :bar
         (oa/select :qux
           (oa/select :baz))))
;; => "{foo bar{qux{baz}}}"
```

```clojure
(o/gql [:foo :bar [:qux :baz]])
;; => "{foo bar{qux baz}}"

(o/gql (oa/select :foo :bar
         (oa/select :qux :baz)))
;; => "{foo bar{qux baz}}"
```

```clojure
(o/gql [:foo [:bar [:baz :qux] :frob]])
;; => "{foo{bar{baz qux} frob}}"

(o/gql (oa/select :foo
         (oa/select :bar
           (oa/select :baz :qux)
           :frob)))
;; => "{foo{bar{baz qux} frob}}"
```

Strings are supported for field names:

```clojure
(o/gql ["query" "foo"])
;; => "{query foo}"

(o/gql (oa/select "query" "foo"))
;; => "{query foo}"
```

Aliases:

```clojure
(o/gql [[:foo {:alias :bar}]])
;; => "{bar:foo}"

(o/gql (oa/select (oa/field :foo (oa/opts (oa/alias :bar)))))
;; => "{bar:foo}"
```

Arguments:

```clojure
(o/gql [[:foo {:arguments {:a 1
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
(o/gql
  (oa/select
    (oa/field :foo
      (oa/opts (oa/arguments :a 1
                             :b "hello world"
                             :c true
                             :d nil
                             :e :foo
                             :f [1 2 3]
                             :g {:frob {:foo 1
                                        :bar 2}}
                             :h :$fooVar)))))
;; => "{foo(a:1, b:\"hello world\", c:true, d:null, e:foo, f:[1 2 3], g:{frob:{foo:1, bar:2}}, h:$fooVar)}"
```

Directives:

```clojure
(o/gql [[:foo {:directives [:bar]}]])
;; => "{foo@bar}"

(o/gql (oa/select (oa/field :foo (oa/opts (oa/directive :bar)))))
;; => "{foo@bar}"
```

Directive arguments:

```clojure
(o/gql [[:foo {:directives [[:bar {:arguments {:qux 123}}]]}]])
;; => "{foo@bar(qux:123)}"

(o/gql (oa/select (oa/field :foo (oa/opts (oa/directive :bar (oa/arguments :qux 123))))))
;; => "{foo@bar(qux:123)}"
```

#### Queries

```clojure
(o/gql [:oksa/query [:foo :bar [:qux [:baz]]]])
;; => "query {foo bar{qux{baz}}}"

(o/gql (oa/query
         (oa/select :foo :bar
           (oa/select :qux
             (oa/select :baz)))))
;; => "query {foo bar{qux{baz}}}"
```

Query names:

```clojure
(o/gql [:oksa/query {:name :Foo} [:foo]])
;; => "query Foo {foo}"

(o/gql (oa/query (oa/opts (oa/name :Foo))
         (oa/select :foo)))
;; => "query Foo {foo}"
```

Query directives:

```clojure
(o/gql [:oksa/query {:directives [:foo]} [:foo]])
;; => "query @foo{foo}"

(o/gql (oa/query (oa/opts (oa/directive :foo))
         (oa/select :foo)))
;; => "query @foo{foo}"
```

```clojure
(o/gql [:oksa/query {:directives [:foo :bar]} [:foo]])
;; => "query @foo @bar{foo}"

(o/gql (oa/query (oa/opts (oa/directives :foo :bar))
         (oa/select :foo)))
;; => "query @foo @bar{foo}"
```

Query directive arguments:

```clojure
(o/gql [:oksa/query {:directives [[:foo {:arguments {:bar 123}}]]} [:foo]])
;; => "query @foo(bar:123){foo}"

(o/gql (oa/query (oa/opts (oa/directive :foo (oa/arguments :bar 123)))
         (oa/select :foo)))
;; => "query @foo(bar:123){foo}"
```

#### Mutations

```clojure
(o/gql [:oksa/mutation [:foo :bar [:qux [:baz]]]])
;; => "mutation {foo bar{qux{baz}}}"

(o/gql (oa/mutation (oa/select :foo :bar
                      (oa/select :qux
                        (oa/select :baz)))))
;; => "mutation {foo bar{qux{baz}}}"
```

```clojure
(o/gql [:oksa/mutation {:name :Foo} [:foo]])
;; => "mutation Foo {foo}"

(o/gql (oa/mutation (oa/opts (oa/name :Foo))
         (oa/select :foo)))
;; => "mutation Foo {foo}"
```

#### Subscriptions

```clojure
(o/gql [:oksa/subscription [:foo :bar [:qux [:baz]]]])
;; => "subscription {foo bar{qux{baz}}}"

(o/gql (oa/subscription (oa/select :foo :bar
                          (oa/select :qux
                            (oa/select :baz)))))
;; => "subscription {foo bar{qux{baz}}}"
```

```clojure
(o/gql [:oksa/subscription {:name :Foo} [:foo]])
;; => "subscription Foo {foo}"

(o/gql (oa/subscription (oa/opts (oa/name :Foo))
         (oa/select :foo)))
;; => "subscription Foo {foo}"
```

#### Variable definitions

Named types are supported:

```clojure
(o/gql [:oksa/query {:variables [:fooVar :FooType]} [:fooField]])
;; => "query ($fooVar:FooType){fooField}"

(o/gql (oa/query (oa/opts (oa/variables :fooVar :FooType))
         (oa/select :fooField)))
;; => "query ($fooVar:FooType){fooField}"
```

```clojure
(o/gql [:oksa/query {:variables [:fooVar :FooType :barVar :BarType]} [:fooField]])
;; => "query ($fooVar:FooType,$barVar:BarType){fooField}"

(o/gql (oa/query (oa/opts (oa/variables :fooVar :FooType :barVar :BarType))
         (oa/select :fooField)))
;; => "query ($fooVar:FooType,$barVar:BarType){fooField}"
```

Lists can be created:

```clojure
(o/gql [:oksa/query {:variables [:fooVar [:oksa/list :FooType]]} [:fooField]])
;; => "query ($fooVar:[FooType]){fooField}"

;; alt
(o/gql [:oksa/query {:variables [:fooVar [:FooType]]} [:fooField]])
;; => "query ($fooVar:[FooType]){fooField}"

;; programmatic
(o/gql (oa/query (oa/opts (oa/variable :fooVar (oa/list :FooType)))
         (oa/select :fooField)))
;; => "query ($fooVar:[FooType]){fooField}"
```

```clojure
(o/gql [:oksa/query {:variables [:fooVar [:oksa/list [:oksa/list :BarType]]]} [:fooField]])
;; => "query ($fooVar:[[BarType]]){fooField}"

;; alt
(o/gql [:oksa/query {:variables [:fooVar [[:BarType]]]} [:fooField]])
;; => "query ($fooVar:[[BarType]]){fooField}"

;; programmatic
(o/gql (oa/query (oa/opts (oa/variable :fooVar (oa/list (oa/list :BarType))))
         (oa/select :fooField)))
;; => "query ($fooVar:[[BarType]]){fooField}"
```

Non-null types can be created:

```clojure
(o/gql [:oksa/query {:variables [:fooVar [:FooType {:non-null true}]]} [:fooField]])
;; => "query ($fooVar:FooType!){fooField}"

;; alt
(o/gql [:oksa/query {:variables [:fooVar :FooType!]} [:fooField]])
;; => "query ($fooVar:FooType!){fooField}"

;; programmatic
(o/gql (oa/query (oa/opts (oa/variable :fooVar (oa/type! :FooType)))
         (oa/select :fooField)))
;; => "query ($fooVar:FooType!){fooField}"
```

```clojure
(o/gql [:oksa/query {:variables [:fooVar [:oksa/list {:non-null true} :BarType]]} [:fooField]])
;; => "query ($fooVar:[BarType]!){fooField}"

;; alt
(o/gql [:oksa/query {:variables [:fooVar [:! :BarType]]} [:fooField]])
;; => "query ($fooVar:[BarType]!){fooField}"

;; programmatic
(o/gql (oa/query (oa/opts (oa/variable :fooVar (oa/list! :BarType)))
         (oa/select :fooField)))
;; => "query ($fooVar:[BarType]!){fooField}"
```

Getting crazy with it:

```clojure
(o/gql [:oksa/query {:variables [:fooVar [:! [:! :BarType!]]]} [:fooField]])
;; => "query ($fooVar:[[BarType!]!]!){fooField}"

(o/gql (oa/query (oa/opts (oa/variable :fooVar (oa/list! (oa/list! (oa/type! :BarType)))))
         (oa/select :fooField)))
;; => "query ($fooVar:[[BarType!]!]!){fooField}"
```

Variable definitions can have directives:

```clojure
(o/gql [:oksa/query {:variables [:foo {:directives [:fooDirective]} :Bar]} [:fooField]])
;; => "query ($foo:Bar @fooDirective){fooField}"

(o/gql (oa/query (oa/opts (oa/variable :foo (oa/opts (oa/directive :fooDirective)) :Bar))
                 (oa/select :fooField)))
;; => "query ($foo:Bar @fooDirective){fooField}"
```

```clojure
(o/gql [:oksa/query {:variables [:foo {:directives [[:fooDirective {:arguments {:fooArg 123}}]]} :Bar]} [:fooField]])
;; => "query ($foo:Bar @fooDirective(fooArg:123)){fooField}"

(o/gql (oa/query (oa/opts (oa/variable :foo (oa/opts (oa/directive :fooDirective (oa/argument :fooArg 123))) :Bar))
                 (oa/select :fooField)))
;; => "query ($foo:Bar @fooDirective(fooArg:123)){fooField}"
```

### Fragments

Fragment definitions can be created:

```clojure
(o/gql [:oksa/fragment {:name :Foo :on :Bar} [:foo]])
;; => "fragment Foo on Bar{foo}"

;; alt
(o/gql [:# {:name :Foo :on :Bar} [:foo]])
;; => "fragment Foo on Bar{foo}"

;; programmatic
(o/gql (oa/fragment (oa/opts
                      (oa/name :Foo)
                      (oa/on :Bar))
         (oa/select :foo)))
;; => "fragment Foo on Bar{foo}"
```

Fragment directives:

```clojure
(o/gql [:oksa/fragment {:name :foo
                        :on :Foo
                        :directives [:fooDirective]}
           [:bar]])
;; => "fragment foo on Foo@fooDirective{bar}"

;; alt
(o/gql (oa/fragment (oa/opts
                      (oa/name :foo)
                      (oa/on :Foo)
                      (oa/directive :fooDirective))
         (oa/select :bar)))
;; => "fragment foo on Foo@fooDirective{bar}"
```

Fragment directive arguments:

```clojure
(o/gql [:oksa/fragment {:name :foo
                        :on :Foo
                        :directives [[:fooDirective {:arguments {:bar 123}}]]} [:bar]])
;; => "fragment foo on Foo@fooDirective(bar:123){bar}"

;; alt
(o/gql (oa/fragment (oa/opts
                      (oa/name :foo)
                      (oa/on :Foo)
                      (oa/directive :fooDirective (oa/argument :bar 123)))
         (oa/select :bar)))
;; => "fragment foo on Foo@fooDirective(bar:123){bar}"
```

Fragment spreads:

```clojure
(o/gql [:foo [:oksa/fragment-spread {:name :bar}]])
;; => "{foo ...bar}"

(o/gql [:foo [:... {:name :bar}]])
;; => "{foo ...bar}"

(o/gql (oa/select :foo (oa/fragment-spread (oa/opts (oa/name :bar)))))
;; => "{foo ...bar}"
```

Fragment spread directives:

```clojure
(o/gql [[:... {:name :foo :directives [:bar]}]])
;; => "{...foo@bar}"

(o/gql (oa/select (oa/fragment-spread (oa/opts (oa/name :foo) (oa/directive :bar)))))
;; => "{...foo@bar}"
```

Fragment spread directive arguments:

```clojure
(o/gql [[:... {:name :foo :directives [[:bar {:arguments {:qux 123}}]]}]])
;; => "{...foo@bar(qux:123)}"

(o/gql (oa/select (oa/fragment-spread (oa/opts (oa/name :foo) (oa/directive :bar (oa/arguments :qux 123))))))
;; => "{...foo@bar(qux:123)}"
```

Inline fragments:

```clojure
(o/gql [:foo [:oksa/inline-fragment [:bar]]])
;; => "{foo ...{bar}}"

;; alt
(o/gql [:foo [:... [:bar]]])
;; => "{foo ...{bar}}"

;; programmatic
(o/gql (oa/select :foo (oa/inline-fragment (oa/select :bar))))
;; => "{foo ...{bar}}"
```

Type condition is supported:

```clojure
(o/gql [:foo [:... {:on :Bar} [:bar]]])
;; => "{foo ...on Bar{bar}}"

(o/gql (oa/select :foo (oa/inline-fragment (oa/opts (oa/on :Bar))
                         (oa/select :bar))))
;; => "{foo ...on Bar{bar}}"
```

Inline fragment directives:

```clojure
(o/gql [[:... {:directives [:foo]} [:bar]]])
;; => "{...@foo{bar}}"

(o/gql (oa/select (oa/inline-fragment (oa/opts (oa/directive :foo))
                    (oa/select :bar))))
;; => "{...@foo{bar}}"
```

Inline fragment directive arguments:

```clojure
(o/gql [[:... {:directives [[:foo {:arguments {:bar 123}}]]} [:foobar]]])
;; => "{...@foo(bar:123){foobar}}"

(o/gql (oa/select (oa/inline-fragment (oa/opts (oa/directive :foo (oa/argument :bar 123)))
                    (oa/select :foobar))))
;; => "{...@foo(bar:123){foobar}}"
```

### Document

Putting it all together:

```clojure
(o/gql [:oksa/document
           [:foo]
           [:oksa/query [:bar]]
           [:oksa/mutation [:qux]]
           [:oksa/subscription [:baz]]
           [:oksa/fragment {:name :foo :on :Foo} [:bar]]])
;; => "{foo}\nquery {bar}\nmutation {qux}\nsubscription {baz}\nfragment foo on Foo{bar}"

;; alt
(o/gql
  (oa/document
    (oa/select :foo)
    (oa/query (oa/select :bar))
    (oa/mutation (oa/select :qux))
    (oa/subscription (oa/select :baz))
    (oa/fragment (oa/opts
                (oa/name :foo)
                (oa/on :Foo))
      (oa/select :bar))))
;; => "{foo}\nquery {bar}\nmutation {qux}\nsubscription {baz}\nfragment foo on Foo{bar}"

(o/gql [:<> [:foo] [:bar]]) ; :<> also supported
;; => "{foo}\n{bar}"
```

More complete example using `oksa.alpha.api`:

```clojure
(o/gql
  (oa/document
    (oa/fragment (oa/opts
                   (oa/name :Kikka)
                   (oa/on :Kukka))
      (oa/select :kikka :kukka))
    (oa/select :ylakikka
      (oa/select :kikka :kukka :kakku)
      ;; v similar to ^, but allow to specify option for single field (only)
      (oa/field :kikka (oa/opts
                         (oa/alias :KIKKA)
                         (oa/directives :kakku :kukka)
                         (oa/directive :kikkaized {:x 1 :y 2 :z 3})))
      (when false (oa/field :conditionalKikka)) ; nils are dropped
      (oa/fragment-spread (oa/opts (oa/name :FooFragment)))
      (oa/inline-fragment (oa/opts (oa/on :Kikka))
        (oa/select :kikka :kukka)))
    (oa/query (oa/opts (oa/name :KikkaQuery))
      (oa/select :specialKikka))
    (oa/mutation (oa/opts
                   (oa/name :saveKikka)
                   (oa/variable :myKikka (oa/opts (oa/default 123)) :KikkaType))
      (oa/select :getKikka))
    (oa/subscription (oa/opts (oa/name :subscribeToKikka))
      (oa/select :realtimeKikka))))
;; => "fragment Kikka on Kukka{kikka kukka}\n{ylakikka{kikka kukka kakku} KIKKA:kikka@kakku @kukka @kikkaized(x:1, y:2, z:3) ...FooFragment ...on Kikka{kikka kukka}}\nquery KikkaQuery {specialKikka}\nmutation saveKikka ($myKikka:KikkaType=123){getKikka}\nsubscription subscribeToKikka {realtimeKikka}"
```

### Name transformation

Oksa supports name transformation:

```clojure
(require '[camel-snake-kebab.core :as csk])

(o/gql*
  {:oksa/name-fn csk/->camelCase}
  [[:foo-bar {:alias :bar-foo
              :directives [:foo-bar]
              :arguments {:foo-arg :bar-value}}
    [:foo-bar]]
   :naked-foo-bar
   [:...
    [:foo-bar]]
   [:... {:on :foo-bar-fragment
          :directives [:foo-bar]}
    [:foo-bar]]])
;; => "{barFoo:fooBar(fooArg:barValue)@fooBar{fooBar} nakedFooBar ...{fooBar} ...on fooBarFragment@fooBar{fooBar}}"
```

Field transformation is supported:

```clojure
(o/gql*
  {:oksa/field-fn csk/->camelCase}
  [:foo-bar
   [:foo-bar]
   :naked-foo-bar
   [:...
    [:foo-bar]]
   [:... {:on :SomeType}
    [:foo-bar]]])
;; => "{fooBar{fooBar} nakedFooBar ...{fooBar} ...on SomeType{fooBar}}"
```

Directives can also be transformed:

```clojure
(o/gql*
  {:oksa/directive-fn csk/->snake_case}
  [[:foo {:directives [:some-thing]}]])
;; => "{foo@some_thing}"
```

You can also override using enums or types:

```clojure
(o/gql*
  {:oksa/name-fn csk/->camelCase
   :oksa/enum-fn csk/->SCREAMING_SNAKE_CASE
   :oksa/type-fn csk/->PascalCase}
  [:oksa/query {:variables [:foo-var {:default :foo-value} :foo-type]}
   [:foobar]])
;; => "query ($fooVar:FooType=FOO_VALUE){foobar}"
```

Local overriding also supported on fields:

```clojure
(o/gql*
  {:oksa/name-fn csk/->camelCase}
  [[:screaming-field {:oksa/field-fn csk/->SCREAMING_SNAKE_CASE}]
   :talking-field])
;; => "{SCREAMING_FIELD talkingField}"
```

An example using a custom transformer to preserve the namespace as part of a field:

```clojure
(defn custom-name [key]
  (str (when (namespace key)
         (str (namespace key) "_"))
       (name key)))

(o/gql* {:oksa/field-fn custom-name} [:employee [:user/name :user/address]])
;; => "{employee{user_name user_address}}"
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

# Oksa CHANGELOG

We use [Break Versioning][breakver]. The version numbers follow a `<major>.<minor>.<patch>` scheme with the following intent:

| Bump    | Intent                                                     |
| ------- | ---------------------------------------------------------- |
| `major` | Major breaking changes -- check the changelog for details. |
| `minor` | Minor breaking changes -- check the changelog for details. |
| `patch` | No breaking changes, ever!!                                |

`-SNAPSHOT` versions are preview versions for upcoming releases.

[breakver]: https://github.com/ptaoussanis/encore/blob/master/BREAK-VERSIONING.md

Oksa is currently [experimental](https://github.com/topics/metosin-experimental).

## 1.2.1-SNAPSHOT

## 1.2.0

Thanks to [@lassemaatta](https://github.com/lassemaatta),
[@lread](https://github.com/lread), and [@terjesb](https://github.com/terjesb)
for contributing to this release.

- Fix to malli 0.18.0 breaking change (#26) [#26](https://github.com/metosin/oksa/pull/26)
  - This fixes a bug where you may have a newer version of malli (>=0.18.0)
    running locally/in your project and run into a `No implementation of
    method: :-form of protocol: #'oksa.alpha.protocol/AST found for class:
    malli.core.Tag` exception due to changes in the malli parser.
  - Fix doesn't aim to be breaking, but the underlying changes to the parser
    were big enough to warrant a minor version bump.
  - Fix also retains backwards compatibility for malli <0.18.0.
- Add `test-doc-blocks` to run tests continuously against examples in README.md [#21](https://github.com/metosin/oksa/pull/21) [#22](https://github.com/metosin/oksa/pull/22) [#23](https://github.com/metosin/oksa/pull/23)
- docs: readme: update babashka badge target [#24](https://github.com/metosin/oksa/pull/24)
- ci: bump build deps [733a8a8](https://github.com/metosin/oksa/commit/733a8a86c05a76059f65840355522e96229c0954) [cb3c6a0](https://github.com/metosin/oksa/commit/cb3c6a03806e0c6e51651fa913aed415dcf4cf7e) [3c93908](https://github.com/metosin/oksa/commit/3c939081a0b440ab8031161416c492893d90190f) [03b95ee](https://github.com/metosin/oksa/commit/03b95ee2482b150a35a648b11aaebafb20c3315c)

## 1.1.0

- Add generative tests [#12](https://github.com/metosin/oksa/pull/12) [a7d0214](https://github.com/metosin/oksa/commit/a7d0214d832f93401974cc1d5e0dd988914fddbc)
- Restrict fragment-spread map to be required [#13](https://github.com/metosin/oksa/pull/13)
- BREAKING CHANGE: Restrict values inside ListValue and ObjectValue [#14](https://github.com/metosin/oksa/pull/14)
- Add missing :min to selection set refs [#15](https://github.com/metosin/oksa/pull/15)
- Add support for persistent hash map formatting [#16](https://github.com/metosin/oksa/pull/16)
- Fixes parser to restrict fragment spread & subsequent selection set [#18](https://github.com/metosin/oksa/pull/18)
- Fixes ClojureScript -name redef warning [#20](https://github.com/metosin/oksa/pull/20)

## 1.0.0

- Adds support for name transformers [#10](https://github.com/metosin/oksa/pull/10)
  - You can provide a custom function to transform names, fields, enums,
    directives, and types. See [README](README.md#name-transformation) for
    examples.
  - With this change, both the data DSL and API now uses the same unparser
    implementation under the hood.
- Fixes parser bug with sequential selection sets [#9](https://github.com/metosin/oksa/pull/10)
- Adds babashka support [#11](https://github.com/metosin/oksa/pull/11)

### Name transformation

Supports top-level definitions and local overrides for transformers.

```clojure
; clj -Sdeps '{:deps {fi.metosin/oksa {:mvn/version "1.0.0"} camel-snake-kebab/camel-snake-kebab {:mvn/version "0.4.3"}}}'

(require '[oksa.core :as o])
(require '[camel-snake-kebab.core :as csk])

(o/gql* {:oksa/name-fn csk/->camelCase
         :oksa/directive-fn csk/->snake_case
         :oksa/enum-fn csk/->SCREAMING_SNAKE_CASE
         :oksa/field-fn csk/->Camel_Snake_Case
         :oksa/type-fn csk/->PascalCase}
  [[:foo-bar {:alias :bar-foo
              :oksa/name-fn csk/->SCREAMING_SNAKE_CASE
              :directives [:foo-bar]
              :arguments {:foo-arg :bar-value}}
    [:foo-bar]]
   :naked-foo-bar
   [:...
    [:foo-bar]]
   [:... {:on :foo-bar-fragment
          :directives [:foo-bar]}
    [:foo-bar]]])

; => "{BAR_FOO:Foo_Bar(FOO_ARG:BAR_VALUE)@foo_bar{Foo_Bar} Naked_Foo_Bar ...{Foo_Bar} ...on fooBarFragment@foo_bar{Foo_Bar}}"
```

## 0.1.0

- Adds `oksa.core/explain` as a convenience function for `malli.core/explain`
- Fixes default value formatting [#6](https://github.com/metosin/oksa/pull/6)
- Introduces `oksa.alpha.api` for programmatic API access
  [#7](https://github.com/metosin/oksa/pull/7)
  - Restricts `FragmentDefinition` and `OperationDefinition` to have just
    single SelectionSet as per spec
  - Fixes `TypeName` to also support string-typed name

## 0.0.1

First release! 🎉

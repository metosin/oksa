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

## 0.1.1-SNAPSHOT

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

(ns oksa.alpha.api
  (:require [oksa.parse]
            [oksa.unparse]
            [oksa.util]
            [oksa.alpha.protocol :refer [Argumented
                                         AST
                                         Serializable
                                         Representable
                                         UpdateableOption]
             :as protocol])
  (:refer-clojure :exclude [name alias set list type]))

(defn -query?
  [x]
  (and (satisfies? AST x)
       (= (protocol/-type x) :oksa/query)))

(defn -mutation?
  [x]
  (and (satisfies? AST x)
       (= (protocol/-type x) :oksa/mutation)))

(defn -subscription?
  [x]
  (and (satisfies? AST x)
       (= (protocol/-type x) :oksa/subscription)))

(defn -fragment?
  [x]
  (and (satisfies? AST x)
       (= (protocol/-type x) :oksa/fragment)))

(defn -field?
  [x]
  (and (satisfies? AST x)
       (= (protocol/-type x) :oksa.parse/Field)))

(defn -naked-field?
  [x]
  (or (and (satisfies? AST x)
           (= (protocol/-type x) :oksa.parse/NakedField))
      (keyword? x)
      (string? x)))

(defn -directive-name? [x]
  (or (and (satisfies? AST x)
           (= (protocol/-type x) :oksa.parse/DirectiveName))
      (keyword? x)
      (string? x)))

(defn -directive? [x]
  (or (and (satisfies? AST x)
           (= (protocol/-type x) :oksa.parse/Directive))
      (keyword? x)
      (string? x)))

(defn -directives? [x]
  (or (and (satisfies? AST x)
           (= (protocol/-type x) :oksa.parse/Directives))
      (keyword? x)
      (string? x)))

(defn -selection-set?
  [x]
  (and (satisfies? AST x)
       (= (protocol/-type x) :oksa.parse/SelectionSet)))

(defn -fragment-spread?
  [x]
  (and (satisfies? AST x)
       (= (protocol/-type x) :oksa/fragment-spread)))

(defn -inline-fragment?
  [x]
  (and (satisfies? AST x)
       (= (protocol/-type x) :oksa/inline-fragment)))

(defn -type?
  [x]
  (or (and (satisfies? AST x)
        (or (= (protocol/-type x) :oksa.parse/TypeName)
            (= (protocol/-type x) :oksa.parse/NamedTypeOrNonNullNamedType)))
      (keyword? x)
      (string? x)))

(defn -list?
  [x]
  (and (satisfies? AST x)
       (= (protocol/-type x) :oksa.parse/ListTypeOrNonNullListType)))

(defn -validate
  ([x msg]
   (-validate x msg {}))
  ([x msg data]
   (when (not x)
     (throw (ex-info msg data)))))

(defn document
  "Composes many executable definitions together to produce a single document.

  `definitions` can be many of:
  - `oksa.alpha.api/query`
  - `oksa.alpha.api/mutation`
  - `oksa.alpha.api/subscription`
  - `oksa.alpha.api/fragment`
  - `oksa.alpha.api/select`

  Tolerates nil entries.

  Example:

  ```
  (document
    (select :foo)
    (query (select :bar))
    (mutation (select :qux))
    (subscription (select :baz))
    (fragment (opts
                (name :foo)
                (on :Foo))
      (select :bar)))
  ```

  See also [Document](https://spec.graphql.org/October2021/#Document)."
  [& definitions]
  (let [definitions* (filter some? definitions)]
    (-validate (and (not-empty definitions*)
                    (every? #(or (-query? %)
                                 (-mutation? %)
                                 (-subscription? %)
                                 (-selection-set? %)
                                 (-fragment? %)) definitions*))
               "invalid definitions, expected `oksa.alpha.api/query`, `oksa.alpha.api/mutation`, `oksa.alpha.api/subscription`, `oksa.alpha.api/fragment`, or `oksa.alpha.api/select`")
    (oksa.parse/-document definitions*)))

(defn query
  "Produces an operation definition of `query` operation type using the fields defined in `selection-set`. Supports query naming, variable definitions, and directives through `opts`.

  Expects `selection-set` to be an instance of `oksa.alpha.api/select`.

  `opts` is an (optional) map and uses the following fields here:

  | field         | description                                                                                                           | API reference                                             |
  |---------------|-----------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------|
  | `:name`       | A query name conforming to [Name](https://spec.graphql.org/October2021/#Name)                                         | `oksa.alpha.api/name`                                     |
  | `:variables`  | Query variable definitions, see also [VariableDefinitions](https://spec.graphql.org/October2021/#VariableDefinitions) | `oksa.alpha.api/variables` or `oksa.alpha.api/variable`   |
  | `:directives` | Query directives, see also [Directives](https://spec.graphql.org/October2021/#Directives)                             | `oksa.alpha.api/directives` or `oksa.alpha.api/directive` |

  Examples:

  ```
  (gql
   (query (select :foo :bar)))
  ; => query {foo bar}

  (gql
   (query (opts (name :Foobar)
                (variables :foo :FooType
                           :bar :BarType)
                (directives :fooDirective :barDirective))
     (select :foo :bar)))
  ; => query Foobar ($foo:FooType,$bar:BarType)@fooDirective @barDirective{foo bar}
  ```

  See also [OperationDefinition](https://spec.graphql.org/October2021/#OperationDefinition)."
  ([selection-set]
   (query nil selection-set))
  ([opts selection-set]
   (-validate (or (and (nil? opts) (-selection-set? selection-set))
                  (and (map? opts) (-selection-set? selection-set))) "expected either `oksa.alpha.api/opts` & `oksa.alpha.api/select`, or `oksa.alpha.api/select`")
   (oksa.parse/-operation-definition :oksa/query opts selection-set)))

(defn mutation
  "Produces an operation definition of `mutation` operation type using the fields defined in `selection-set`.

  Supports query naming, variable definitions, and directives through `opts`, and expects `selection-set` to be an instance of `oksa.alpha.api/select`.

  `opts` is an (optional) map and uses the following fields here:

  | field         | description                                                                                                              | API reference                                             |
  |---------------|--------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------|
  | `:name`       | Mutation name conforming to [Name](https://spec.graphql.org/October2021/#Name)                                           | `oksa.alpha.api/name`                                     |
  | `:variables`  | Mutation variable definitions, see also [VariableDefinitions](https://spec.graphql.org/October2021/#VariableDefinitions) | `oksa.alpha.api/variables` or `oksa.alpha.api/variable`   |
  | `:directives` | Mutation directives, see also [Directives](https://spec.graphql.org/October2021/#Directives)                             | `oksa.alpha.api/directives` or `oksa.alpha.api/directive` |

  Examples:

  ```
  (gql
   (mutation (select :foo :bar)))
  ; => mutation {foo bar}

  (gql
   (mutation (opts (name :Foobar)
                   (variables :foo :FooType
                              :bar :BarType)
                   (directives :fooDirective :barDirective))
             (select :foo :bar)))
  ; => mutation Foobar ($foo:FooType,$bar:BarType)@fooDirective @barDirective{foo bar}
  ```

  See also [OperationDefinition](https://spec.graphql.org/October2021/#OperationDefinition)."
  ([selection-set]
   (mutation nil selection-set))
  ([opts selection-set]
   (-validate (or (and (nil? opts) (-selection-set? selection-set))
                  (and (map? opts) (-selection-set? selection-set))) "expected either `oksa.alpha.api/opts` & `oksa.alpha.api/select`, or `oksa.alpha.api/select`")
   (oksa.parse/-operation-definition :oksa/mutation opts selection-set)))

(defn subscription
  "Produces an operation definition of `subscription` operation type using the fields defined in `selection-set`.

  Supports query naming, variable definitions, and directives through `opts`, and expects `selection-set` to be an instance of `oksa.alpha.api/select`.

  `opts` is an (optional) map and uses the following fields here:

  | field         | description                                                                                                                  | API reference                                             |
  |---------------|------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------|
  | `:name`       | A subscription name conforming to [Name](https://spec.graphql.org/October2021/#Name)                                         | `oksa.alpha.api/name`                                     |
  | `:variables`  | Subscription variable definitions, see also [VariableDefinitions](https://spec.graphql.org/October2021/#VariableDefinitions) | `oksa.alpha.api/variables` or `oksa.alpha.api/variable`   |
  | `:directives` | Subscription directives, see also [Directives](https://spec.graphql.org/October2021/#Directives)                             | `oksa.alpha.api/directives` or `oksa.alpha.api/directive` |

  Examples:

  ```
  (gql
   (subscription (select :foo :bar)))
  ; => subscription {foo bar}

  (gql
   (subscription (opts (name :Foobar)
                       (variables :foo :FooType
                                  :bar :BarType)
                       (directives :fooDirective :barDirective))
                 (select :foo :bar)))
  ; => subscription Foobar ($foo:FooType,$bar:BarType)@fooDirective @barDirective{foo bar}
  ```

  See also [OperationDefinition](https://spec.graphql.org/October2021/#OperationDefinition)."
  ([selection-set]
   (subscription nil selection-set))
  ([opts selection-set]
   (-validate (or (and (nil? opts) (-selection-set? selection-set))
                  (and (map? opts) (-selection-set? selection-set))) "expected either `oksa.alpha.api/opts` & `oksa.alpha.api/select`, or `oksa.alpha.api/select`")
   (oksa.parse/-operation-definition :oksa/subscription opts selection-set)))

(defn fragment
  "Produces a fragment definition using the fields defined in `selection-set`.

  Expects `:name` and `:on` fields under `opts` arg, and expects `selection-set` to be an instance of `oksa.alpha.api/select`.

  `opts` is a map and uses the following fields here:

  | field         | required | description                                                                                   | API reference                                             |
  |---------------|----------|-----------------------------------------------------------------------------------------------|-----------------------------------------------------------|
  | `:name`       | true     | A fragment name conforming to [Name](https://spec.graphql.org/October2021/#Name)              | `oksa.alpha.api/name`                                     |
  | `:on`         | true     | Type condition, see also [TypeCondition](https://spec.graphql.org/October2021/#TypeCondition) | `oksa.alpha.api/on`                                       |
  | `:directives` | false    | Fragment directives, see also [Directives](https://spec.graphql.org/October2021/#Directives)  | `oksa.alpha.api/directives` or `oksa.alpha.api/directive` |

  Examples:

  ```
  (gql
   (fragment (opts (name :FooFragment)
                   (on :FooType))
     (select :foo :bar)))
  ; => fragment FooFragment on FooType{foo bar}

  (gql
   (fragment (opts (name :FooFragment)
                   (on :FooType)
                   (directives :fooDirective :barDirective))
     (select :foo :bar)))
  ; => fragment FooFragment on FooType@fooDirective @barDirective{foo bar}
  ```

  See also [FragmentDefinition](https://spec.graphql.org/October2021/#FragmentDefinition)."
  [opts selection-set]
  (-validate (:name opts) "expected `oksa.alpha.api/name` on `opts`")
  (-validate (:on opts) "expected `oksa.alpha.api/on` on `opts`")
  (oksa.parse/-fragment opts selection-set))

(declare -naked-field)

(defn select*
  "Produces a selection set using `selections`.

  Expects an entry in `selections` to be an instance of:
  - `oksa.alpha.api/field`
  - keyword (representing a naked field)
  - `oksa.alpha.api/fragment-spread`
  - `oksa.alpha.api/inline-fragment`

  Tolerates nil entries.

  `opts` is an (optional) map and uses the following fields here:

  | field           | description                                                                      |
  |-----------------|----------------------------------------------------------------------------------|
  | `:oksa/name-fn` | A function that accepts a single arg `name` and expects a stringifiable output.  |
  |                 | Applied recursively to all contained fields & selections.                        |

  Examples:

  ```
  (gql
   (select :foo :bar))
  ; => {foo bar}

  (gql
   (select :foo :bar
     (select :qux :baz)
     (field :foobar (opts (alias :BAR)))
     (when false (field :conditionalFoo))
     (fragment-spread (opts (name :FooFragment)))
     (inline-fragment (opts (on :KikkaType))
       (select :kikka :kukka))))
  ; => {foo bar{qux baz} BAR:foobar ...FooFragment ...on KikkaType{kikka kukka}}
  ```

  See also [SelectionSet](https://spec.graphql.org/October2021/#SelectionSet).
  "
  [opts & selections]
  (let [selections* (->> selections (filter some?) (map (fn [selection]
                                                          (if (-naked-field? selection)
                                                            (oksa.parse/-naked-field opts selection)
                                                            selection))))]
    (-validate (not (-selection-set? (first selections*))) "first selection cannot be `oksa.alpha.api/select`")
    (-validate (and (not-empty selections*)
                    (every? #(or (-field? %)
                                 (-naked-field? %)
                                 (-selection-set? %)
                                 (-fragment-spread? %)
                                 (-inline-fragment? %)) selections*))
               "invalid selections, expected `oksa.alpha.api/field`, keyword (naked field), `oksa.alpha.api/fragment-spread`, or `oksa.alpha.api/inline-fragment`")
    (oksa.parse/-selection-set opts selections*)))

(defn select
  "Produces a selection set using `selections`.

  Expects an entry in `selections` to be an instance of:
  - `oksa.alpha.api/field`
  - keyword (representing a naked field)
  - `oksa.alpha.api/fragment-spread`
  - `oksa.alpha.api/inline-fragment`

  Tolerates nil entries.

  Examples:

  ```
  (gql
   (select :foo :bar))
  ; => {foo bar}

  (gql
   (select :foo :bar
     (select :qux :baz)
     (field :foobar (opts (alias :BAR)))
     (when false (field :conditionalFoo))
     (fragment-spread (opts (name :FooFragment)))
     (inline-fragment (opts (on :KikkaType))
       (select :kikka :kukka))))
  ; => {foo bar{qux baz} BAR:foobar ...FooFragment ...on KikkaType{kikka kukka}}
  ```

  See also [SelectionSet](https://spec.graphql.org/October2021/#SelectionSet).
  "
  [& selections]
  (apply select* nil selections))

(defn field
  "Produces a field using `name`. Can be used directly within `oksa.alpha.api/select` when you need to provide options (eg. arguments, directives) for a particular field.

  Multiple arities are supported:
  - `(field name)`
  - `(field name opts)`
  - `(field name selection-set)`
  - `(field name opts selection-set)`

  Expects `name` to conform to [Name](https://spec.graphql.org/October2021/#Name), and expects `selection-set` to be an instance of `oksa.alpha.api/select`.

  `opts` is an (optional) map and uses the following fields here:

  | field         | description                                                                               | API reference                                             |
  |---------------|-------------------------------------------------------------------------------------------|-----------------------------------------------------------|
  | `:alias`      | A field alias conforming to [Alias](https://spec.graphql.org/October2021/#Alias)          | `oksa.alpha.api/alias`                                    |
  | `:arguments`  | Field arguments, see also [Arguments](https://spec.graphql.org/October2021/#Arguments)    | `oksa.alpha.api/arguments` or `oksa.alpha.api/argument`   |
  | `:directives` | Field directives, see also [Directives](https://spec.graphql.org/October2021/#Directives) | `oksa.alpha.api/directives` or `oksa.alpha.api/directive` |

  Examples:

  ```
  (gql
   (select
     (field :foo)
     (field :bar)))
  ; => {foo bar}

  (gql
   (select
     (field :foo (opts (alias :FOO)))))
  ; => {FOO:foo}

  (gql
   (select
     (field :foo (select :bar))))
  ; => {foo{bar}}

  (gql
   (select
     (field :foo (opts (alias :FOO)
                       (directives :fooDirective :barDirective)
                       (arguments :fooArg \"fooValue\"
                                  :barArg 123))
       (select :bar))))
  ; => {FOO:foo(fooArg:\"fooValue\", barArg:123)@fooDirective @barDirective{bar}}
  ```

  See tests for more examples.

  See also [Field](https://spec.graphql.org/October2021/#Field)."
  ([name]
   (field name nil nil))
  ([name selection-set-or-opts]
   (-validate (or (-selection-set? selection-set-or-opts)
                  (map? selection-set-or-opts)
                  (nil? selection-set-or-opts)) "expected either `oksa.alpha.api/opts` or `oksa.alpha.api/select`")
   (cond
     (-selection-set? selection-set-or-opts) (field name nil selection-set-or-opts)
     (map? selection-set-or-opts) (field name selection-set-or-opts nil)
     (nil? selection-set-or-opts) (field name nil nil)))
  ([name opts selection-set]
   (when (some? opts)
     (-validate (map? opts) "expected `oksa.alpha.api/opts`"))
   (when (some? selection-set)
     (-validate (-selection-set? selection-set) "expected `oksa.alpha.api/select`"))
   (oksa.parse/-field name opts selection-set)))

(declare -transform-map)

(defn fragment-spread
  "Produces a fragment spread using `:name` under `opts`. Can be used directly within `oksa.alpha.api/select`.

  Expects `:name` under `opts` arg.

  `opts` is a map and uses the following fields here:

  | field         | required | description                                                                                   | API reference                                             |
  |---------------|----------|-----------------------------------------------------------------------------------------------|-----------------------------------------------------------|
  | `:name`       | true     | A fragment name conforming to [Name](https://spec.graphql.org/October2021/#Name)              | `oksa.alpha.api/name`                                     |
  | `:directives` | false    | Fragment directives, see also [Directives](https://spec.graphql.org/October2021/#Directives)  | `oksa.alpha.api/directives` or `oksa.alpha.api/directive` |

  Examples:

  ```
  (gql
   (select (fragment-spread (opts (name :Foo)))))
  ; => {...Foo}

  (gql
    (select
      (fragment-spread (opts
                         (name :Foo)
                         (directives :fooDirective :barDirective)))))
  ; => {...Foo@fooDirective @barDirective}
  ```

  See also [FragmentSpread](https://spec.graphql.org/October2021/#FragmentSpread)."
  [opts]
  (-validate (some? (:name opts)) "expected `oksa.alpha.api/name` for `opts`")
  (oksa.parse/-fragment-spread opts))

(defn inline-fragment
  "Produces an inline fragment using the fields defined in `selection-set`. Can be used directly within `oksa.alpha.api/select`.

  Expects `selection-set` to be an instance of `oksa.alpha.api/select`.

  `opts` is an (optional) map and uses the following fields here:

  | field         | description                                                                                   | API reference                                             |
  |---------------|-----------------------------------------------------------------------------------------------|-----------------------------------------------------------|
  | `:on`         | Type condition, see also [TypeCondition](https://spec.graphql.org/October2021/#TypeCondition) | `oksa.alpha.api/on`                                       |
  | `:directives` | Fragment directives, see also [Directives](https://spec.graphql.org/October2021/#Directives)  | `oksa.alpha.api/directives` or `oksa.alpha.api/directive` |

  Examples:

  ```
  (gql
   (select
     (inline-fragment (select :foo :bar))))
  ; => {...{foo}}

  (gql
   (select
     (inline-fragment (opts (on :FooType)
                            (directives :fooDirective :barDirectives))
       (select :foo :bar))))
  ; => {...on FooType@fooDirective @barDirectives{foo bar}}
  ```

  See also [InlineFragment](https://spec.graphql.org/October2021/#InlineFragment)."
  ([selection-set]
   (inline-fragment nil selection-set))
  ([opts selection-set]
   (-validate (or (and (nil? opts) (-selection-set? selection-set))
                  (and (map? opts) (-selection-set? selection-set))) "expected either `oksa.alpha.api/opts`, `oksa.alpha.api/select` or `oksa.alpha.api/opts` & `oksa.alpha.api/select` as vargs")
   (oksa.parse/-inline-fragment opts selection-set)))

(defn opts
  "Produces a map of `options`, a collection of `oksa.alpha.protocol/UpdateableOption`s. Output is a `clojure.lang.IPersistentMap`.

  Tolerates nil entries.

  Each instance of `oksa.alpha.protocol/UpdateableOption` implements their own behavior on where & how to update `opts` with the instance's own value:

  |  API reference              | Key           | Expected update behavior |
  |-----------------------------|---------------|--------------------------|
  | `oksa.alpha.api/alias`      | `:alias`      | Replaces value           |
  | `oksa.alpha.api/argument`   | `:arguments`  | Associates value         |
  | `oksa.alpha.api/arguments`  | `:arguments`  | Associates value(s)      |
  | `oksa.alpha.api/default`    | `:default`    | Replaces value           |
  | `oksa.alpha.api/directive`  | `:directive`  | Appends value            |
  | `oksa.alpha.api/directives` | `:directives` | Appends value(s)         |
  | `oksa.alpha.api/name`       | `:name`       | Replaces value           |
  | `oksa.alpha.api/on`         | `:on`         | Replaces value           |
  | `oksa.alpha.api/variable`   | `:variables`  | Appends value            |
  | `oksa.alpha.api/variables`  | `:variables`  | Appends value(s)         |

  Examples:

  ```
  (opts (alias :bar))
  ; => {:alias :bar}

  (opts
   (name :foo)
   (on :Foo)
   (directives :fooDirective :barDirective))
  ; => {:name :foo, :on :Foo, :directives [:fooDirective :barDirective]}
  ```

  See tests for more examples."
  [& options]
  (let [vargs* (filter some? options)]
    (-validate (every? #(satisfies? UpdateableOption %) vargs*)
               (str "invalid option, expected: "
                    "`oksa.alpha.api/on`, `oksa.alpha.api/name`, `oksa.alpha.api/alias`, "
                    "`oksa.alpha.api/directive`, `oksa.alpha.api/directives`, `oksa.alpha.api/argument`, "
                    "`oksa.alpha.api/arguments`, `oksa.alpha.api/variable`, "
                    "`oksa.alpha.api/variables`, or `oksa.alpha.api/default`"))
    (apply oksa.parse/-opts vargs*)))

(defn on
  "Returns a type condition under key `:on` using `name` which should conform to [NamedType](https://spec.graphql.org/October2021/#NamedType). Used directly within `oksa.alpha.api/opts`.

  Example:

  ```
  (opts
   (name :foo)
   (on :Foo))
  ; => {:name :foo, :on :Foo}
  ```

  See also [TypeCondition](https://spec.graphql.org/October2021/#TypeCondition)."
  [name]
  (oksa.parse/-on name))

(defn name
  "Returns `name` under key `:name`. Name should conform to [Name](https://spec.graphql.org/October2021/#Name). Used directly within `oksa.alpha.api/opts`.

  Example:

  ```
  (opts
   (name :foo)
   (on :Foo))
  ; => {:name :foo, :on :Foo}
  ```

  See [Name](https://spec.graphql.org/October2021/#Name)."
  [name]
  (oksa.parse/-name name))

(defn alias
  "Returns an alias under key `:alias` using `name` which should conform to [Name](https://spec.graphql.org/October2021/#Name). Used directly within `oksa.alpha.api/opts`.

  Example:

  ```
  (opts
   (alias :foo))
  ; => {:alias :foo}
  ```

  See also [Alias](https://spec.graphql.org/October2021/#Alias)."
  [name]
  (oksa.parse/-alias name))

(defn directive
  "Returns a directive under key `:directives` using `name` which should conform to [Name](https://spec.graphql.org/October2021/#Name). Used directly within `oksa.alpha.api/opts`.

  Optionally accepts `arguments`, which is an instance of `oksa.alpha.api/Argumented` (ie. `oksa.alpha.api/argument` or `oksa.alpha.api/arguments`).

  Examples:

  ```
  (opts
   (directive :foo))
  ; => {:directives [[:foo {}]]}

  (opts
   (directive :foo (arguments :bar 123)))
  ; => {:directives [[:foo {:arguments {:bar 123}}]]}
  ```

  See also [Directive](https://spec.graphql.org/October2021/#Directive)."
  ([name]
   (directive name nil))
  ([name arguments]
   (oksa.parse/-directive name arguments)))

(defn directives
  "Returns `directives` under key `:directives`. Used directly within `oksa.alpha.api/opts`.

  Expects `directives` is a varargs consisting of keywords (naked directives) or instances of `oksa.alpha.api/directive`.

  Tolerates nil entries.

  Examples:

  ```
  (opts
   (directives :foo :bar))
  ; => {:directives [:foo :bar]}

  (opts
   (directive :foo)
   (directive :bar)
   (directives :frob :nitz))
  ; => {:directives [[:foo {}] [:bar {}] :frob :nitz]}

  ;; mixed use example
  (opts
   (directives
    :foo
    (directive :bar)
    :foobar))
  ; => {:directives [:foo [:bar {}] :foobar]}
  ```

  See also [Directives](https://spec.graphql.org/October2021/#Directives)."
  [& directives]
  (let [directives* (->> directives (filter some?) (map (fn [x]
                                                          (if (-directive-name? x)
                                                            (oksa.parse/-directive-name x)
                                                            x))))]
    (-validate (and (not-empty directives*)
                    (every? #(or (-directive-name? %)
                                 (-directive? %)) directives*))
               "invalid directives, expected `oksa.alpha.api/directive` or naked directive (keyword)")
    (oksa.parse/-directives directives*)))

(defn argument
  "Returns an argument under key `:arguments`. Used directly within `oksa.alpha.api/opts`.

  Expects that `name` is a string or a keyword (that conforms to [Name](https://spec.graphql.org/October2021/#Name)) and that `value` conforms to [Value](https://spec.graphql.org/October2021/#Value).

  Can also be used directly within `oksa.alpha.api/directive`.

  Examples:

  ```
  (opts (argument :foo 1))
  ; => {:arguments {:foo 1}}

  (api/opts (api/directive :foo (api/argument :bar 123)))
  ; => {:directives [[:foo {:arguments {:bar 123}}]]}
  ```

  See also [Argument](https://spec.graphql.org/October2021/#Argument)."
  [name value]
  (oksa.parse/-argument name value))

(defn arguments
  "Returns `arguments` under key `:arguments`. Used directly within `oksa.alpha.api/opts`.

  Expects `arguments` to be pair(s) of names and values where name is a string or a keyword (that conforms to [Name](https://spec.graphql.org/October2021/#Name)) and where value conforms to [Value](https://spec.graphql.org/October2021/#Value).

  Can also be used directly within `oksa.alpha.api/directive`.

  Example:

  ```
  (opts (arguments :foo 1 :bar \"foobar\"))
  ; => {:arguments {:foo 1, :bar \"foobar\"}}

  (opts (directive :foo (arguments :qux 123 :baz \"frob\")))
  ; => {:directives [[:foo {:arguments {:qux 123, :baz \"frob\"}}]]}
  ```

  See also [Arguments](https://spec.graphql.org/October2021/#Arguments)."
  [& arguments]
  (-validate (= (mod (count arguments) 2) 0) "uneven amount of arguments, expected key-value pairs")
  (oksa.parse/-arguments (->> arguments
                              (partition 2)
                              (map vec)
                              (into {}))))

(defn type
  "Returns a named type using `type-name`.

  See also [NamedType](https://spec.graphql.org/October2021/#NamedType)."
  [type-name]
  (oksa.parse/-type type-name))

(defn type!
  "Returns a non-nil named type using `type-name`.

  See also [NonNullType](https://spec.graphql.org/October2021/#NonNullType)."
  [type-name]
  (oksa.parse/-type! type-name))

(defn list
  "Returns a list type using `type-or-list`.

  Expects `type-or-list` to be an instance of `oksa.alpha.api/type`, `oksa.alpha.api/type!`, `oksa.alpha.api/list`, or `oksa.alpha.api/type!`.

  Examples:

  ```
  (opts (variable :foo (list :FooType)))
  ; => {:variables [:foo [:oksa/list {:non-null false} :FooType]]}

  (opts (variable :foo (list (list :BarType))))
  ; => {:variables [:foo
  ;                 [:oksa/list
  ;                  {:non-null false}
  ;                  [:oksa/list {:non-null false} :BarType]]]}
  ```

  See also [ListType](https://spec.graphql.org/October2021/#ListType)."
  [type-or-list]
  (-validate (or (-type? type-or-list) (-list? type-or-list))
             "invalid type given, expected `oksa.alpha.api/type`, `oksa.alpha.api/type!`, keyword (naked type), `oksa.alpha.api/list`, or `oksa.alpha.api/list!`")
  (oksa.parse/-list {:non-null false} type-or-list))

(defn list!
  "Returns a non-nil list type using `type-or-list`.

  Expects `type-or-list` to be an instance of `oksa.alpha.api/type`, `oksa.alpha.api/type!`, `oksa.alpha.api/list`, or `oksa.alpha.api/type!`.

  Examples:

  ```
  (opts (variable :foo (list! :FooType)))
  ; => {:variables [:foo [:oksa/list {:non-null true} :FooType]]}

  (opts (variable :foo (list (list! :BarType))))
  ; => {:variables [:foo
                    [:oksa/list
                     {:non-null false}
                     [:oksa/list {:non-null true} :BarType]]]}

  (opts (variable :foo (list! (list! :BarType))))
  ; => {:variables [:foo
                    [:oksa/list
                     {:non-null true}
                     [:oksa/list {:non-null true} :BarType]]]}
  ```

  See also [NonNullType](https://spec.graphql.org/October2021/#NonNullType)."
  [type-or-list]
  (oksa.parse/-list {:non-null true} type-or-list))

(defn variable
  "Returns a variable definition under `:variables` key using `variable-name` (which can be a string or a keyword) and `variable-type`. Used directly within `oksa.alpha.api/opts`.

  `opts` is an (optional) map and uses the following field(s) here:

  | field         | description                                                                                              | API reference                                             |
  |---------------|----------------------------------------------------------------------------------------------------------|-----------------------------------------------------------|
  | `:default`    | Default value for variable, see also [DefaultValue](https://spec.graphql.org/October2021/#DefaultValue)  | `oksa.alpha.api/default`                                  |
  | `:directives` | Variable directives, see also [Directives](https://spec.graphql.org/October2021/#Directives)             | `oksa.alpha.api/directives` or `oksa.alpha.api/directive` |

  Examples:

  ```
  (opts (variable :fooVar :FooType))
  ; => {:variables [:fooVar :FooType]}

  (opts (variable :foo (opts (directives :fooDirective :barDirective)
                             (default 123))
          :Bar))
  ; => {:variables [:foo
  ;                 {:directives [:fooDirective :barDirective], :default 123}
  ;                 :Bar]}
  ```

  See also [VariableDefinitions](https://spec.graphql.org/October2021/#VariableDefinitions)."
  ([variable-name variable-type]
   (variable variable-name nil variable-type))
  ([variable-name opts variable-type]
   (-validate (or (-type? variable-type) (-list? variable-type))
              "invalid type given, expected `oksa.alpha.api/type`, `oksa.alpha.api/type!`, keyword (naked type), `oksa.alpha.api/list`, or `oksa.alpha.api/list!`")
   (oksa.parse/-variable variable-name opts variable-type)))

(defn variables
  "Returns `variable-definitions` under key `:variables`. Used directly within `oksa.alpha.api/opts`.

  Expects `variable-definitions` to be pair(s) of variables and types where variable is a string or a keyword (that conforms to [Variable](https://spec.graphql.org/October2021/#Variable)) and where type is a string or keyword that conforms to [Type](https://spec.graphql.org/October2021/#Type).

  Example:

  ```
  (opts (variables :fooVar :FooType
                   :barVar :BarType))
  ; => {:variables [:fooVar :FooType :barVar :BarType]}
  ```

  See also [VariableDefinitions](https://spec.graphql.org/October2021/#VariableDefinitions)."
  [& variable-definitions]
  (-validate (= (mod (count variable-definitions) 2) 0) "uneven amount of arguments, expected key-value pairs")
  (let [variable-definitions* (partition 2 variable-definitions)]
    (-validate (every? (fn [[_ v]] (or (-type? v) (-list? v))) variable-definitions*) "invalid variable types given, expected `oksa.alpha.api/type`, `oksa.alpha.api/type!`, keyword (naked type), `oksa.alpha.api/list`, or `oksa.alpha.api/list!`")
    (oksa.parse/-variables variable-definitions*)))

(defn default
  "Returns default `value` under `:default` key. Used directly within `oksa.alpha.api/opts`.

  Example:

  ```
  (opts (variable :fooVar (opts (default 123)) :Foo))
  ; => {:variables [:fooVar {:default 123} :Foo]}
  ```

  See also [DefaultValue](https://spec.graphql.org/October2021/#DefaultValue)."
  [value]
  (oksa.parse/-default value))

(defn name-fn
  "Sets name transformer function `f` to `:oksa/name-fn`. Function `f` is invoked against all instances of
   `:oksa.parse/Name`."
  [f]
  (reify
    UpdateableOption
    (-update-key [_] :oksa/name-fn)
    (-update-fn [_] (constantly f))))

(defn gql
  "Returns a GraphQL request string for a given `obj`.

  Expects `obj` to implement `oksa.alpha.protocol/Representable` or will throw.

  The following functions implement `oksa.alpha.protocol/Representable`:
  - `oksa.alpha.api/document`
  - `oksa.alpha.api/select`
  - `oksa.alpha.api/query`
  - `oksa.alpha.api/mutation`
  - `oksa.alpha.api/subscription`
  - `oksa.alpha.api/fragment`

  `opts` is an (optional) map and uses the following fields here:

  | field           | description                                                                      |
  |-----------------|----------------------------------------------------------------------------------|
  | `:oksa/name-fn` | A function that accepts a single arg `name` and expects a stringifiable output.  |
  |                 | Applied recursively to all contained fields & selections.                        |"
  ([obj]
   (gql nil obj))
  ([opts obj]
   (-validate (satisfies? Representable obj) "Object must be Representable (one of `oksa.alpha.api/document`, `oksa.alpha.api/select`, `oksa.alpha.api/query`, `oksa.alpha.api/mutation`, `oksa.alpha.api/subscription`, or `oksa.alpha.api/fragment`)")
   (protocol/-gql obj opts)))

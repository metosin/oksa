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

;; TODO: clean up
(defn -parse-or-throw
  [type form parser message]
  (let [retval (parser form)]
    (if (not= retval :malli.core/invalid)
      retval
      (throw (ex-info message
                      (cond
                        (= oksa.util/mode "debug") {:malli.core/explain (malli.core/explain
                                                                          (oksa.parse/-graphql-dsl-lang type)
                                                                          form)}
                        (= oksa.util/mode "default") {}
                        :else (throw (ex-info "incorrect `oksa.api/mode` (system property), expected one of `default` or `debug`" {:mode oksa.util/mode}))))))))

(defn -get-oksa-opts
  [opts]
  (into {} (filter #(= (namespace (first %)) "oksa") opts)))

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
    (let [form (into [:oksa/document {}] (map protocol/-form definitions*))
          [type opts _definitions :as document*] (-parse-or-throw :oksa.parse/Document
                                                                  form
                                                                  oksa.parse/-graphql-dsl-parser
                                                                  "invalid document")]
      (reify
        AST
        (-type [_] type)
        (-opts [_] opts)
        (-parsed-form [_] document*)
        (-form [_] form)
        Serializable
        (-unparse [_ opts]
          (oksa.unparse/unparse-document opts definitions*))
        Representable
        (-gql [this opts] (protocol/-unparse this opts))))))

(defn -operation-definition
  [operation-type opts selection-set]
  (let [opts (or opts {})
        form [operation-type opts (protocol/-form selection-set)]
        [type opts _selection-set :as parsed-form] (-parse-or-throw :oksa.parse/OperationDefinition
                                                                    form
                                                                    (oksa.parse/-operation-definition-parser opts)
                                                                    "invalid operation definition")]
    (reify
      AST
      (-type [_] type)
      (-opts [_] (-> opts
                     (update :directives (partial oksa.util/transform-malli-ast oksa.parse/-transform-map))
                     (update :variables (partial oksa.util/transform-malli-ast oksa.parse/-transform-map))))
      (-parsed-form [_] parsed-form)
      (-form [_] form)
      Serializable
      (-unparse [this opts]
        (oksa.unparse/unparse-operation-definition
         (clojure.core/name (protocol/-type this))
         (merge (-get-oksa-opts opts) (protocol/-opts this))
         selection-set))
      Representable
      (-gql [this opts] (protocol/-unparse this opts)))))

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
   (-operation-definition :oksa/query opts selection-set)))

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
   (-operation-definition :oksa/mutation opts selection-set)))

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
   (-operation-definition :oksa/subscription opts selection-set)))

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
  (let [form [:oksa/fragment opts (protocol/-form selection-set)]
        [type opts _selection-set
         :as fragment*] (-parse-or-throw :oksa.parse/FragmentDefinition
                                         form
                                         (oksa.parse/-fragment-definition-parser opts)
                                         "invalid fragment definition")]
    (reify
      AST
      (-type [_] type)
      (-opts [_] (update opts :directives (partial oksa.util/transform-malli-ast oksa.parse/-transform-map)))
      (-parsed-form [_] fragment*)
      (-form [_] form)
      Serializable
      (-unparse [this opts]
        (oksa.unparse/unparse-fragment-definition
          (merge (-get-oksa-opts opts) (protocol/-opts this))
          selection-set))
      Representable
      (-gql [this opts] (protocol/-unparse this opts)))))

(declare -naked-field)

(defn -selection-set
  [opts selections]
  (let [form (mapv #(if (satisfies? AST %) (protocol/-form %) %) selections)
        [type _selections
         :as parsed-form] (-parse-or-throw :oksa.parse/SelectionSet
                                           form
                                           (oksa.parse/-selection-set-parser opts)
                                           "invalid selection-set")]
    (reify
      AST
      (-type [_] type)
      (-opts [_] opts)
      (-parsed-form [_] parsed-form)
      (-form [_] form)
      Serializable
      (-unparse [this opts]
        (oksa.unparse/unparse-selection-set (merge (-get-oksa-opts opts)
                                                   (protocol/-opts this))
                                            selections))
      Representable
      (-gql [this opts] (protocol/-unparse this
                                           (merge (-get-oksa-opts opts)
                                                  (protocol/-opts this)))))))

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
                                                            (-naked-field opts selection)
                                                            selection))))]
    (-validate (not (-selection-set? (first selections*))) "first selection cannot be `oksa.alpha.api/select`")
    (-validate (and (not-empty selections*)
                    (every? #(or (-field? %)
                                 (-naked-field? %)
                                 (-selection-set? %)
                                 (-fragment-spread? %)
                                 (-inline-fragment? %)) selections*))
               "invalid selections, expected `oksa.alpha.api/field`, keyword (naked field), `oksa.alpha.api/fragment-spread`, or `oksa.alpha.api/inline-fragment`")
    (-selection-set opts selections*)))

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

(defn -field
  [name opts selection-set]
  (let [opts (or opts {})
        form (cond-> [name opts]
                     (some? selection-set) (conj (protocol/-form selection-set)))
        [type [_field-name field-opts _selection-set*]
         :as field*] (-parse-or-throw :oksa.parse/Field
                                      form
                                      (oksa.parse/-field-parser opts)
                                      "invalid field")]
    (reify
      AST
      (-type [_] type)
      (-opts [_] (update field-opts
                         :directives
                         (partial oksa.util/transform-malli-ast oksa.parse/-transform-map)))
      (-parsed-form [_] field*)
      (-form [_] form)
      Serializable
      (-unparse [this opts] (oksa.unparse/unparse-field name
                                                        (merge (-get-oksa-opts opts)
                                                               (protocol/-opts this))
                                                        selection-set)))))

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
   (-field name opts selection-set)))

(defn -naked-field
  [opts name]
  (let [naked-field* (-parse-or-throw :oksa.parse/NakedField
                                      name
                                      (oksa.parse/-naked-field-parser opts)
                                      "invalid naked field")]
    (reify
      AST
      (-type [_] :oksa.parse/NakedField)
      (-opts [_] {})
      (-parsed-form [_] naked-field*)
      (-form [_] naked-field*)
      Serializable
      (-unparse [_ opts]
        (let [name-fn (:oksa/name-fn opts)]
          (-parse-or-throw :oksa.parse/Name
                           (if name-fn
                             (name-fn naked-field*)
                             (clojure.core/name naked-field*))
                           (oksa.parse/-name-parser {:oksa/strict true})
                           "invalid naked field"))))))

(declare -transform-map)

(defn -fragment-spread
  [opts]
  (let [form [:oksa/fragment-spread opts]
        [type opts :as fragment-spread*] (-parse-or-throw :oksa.parse/FragmentSpread
                                                          form
                                                          (oksa.parse/-fragment-spread-parser opts)
                                                          "invalid fragment spread parser")]
    (reify
      AST
      (-type [_] type)
      (-opts [_]
        (update opts
                :directives
                (partial oksa.util/transform-malli-ast
                         -transform-map)))
      (-parsed-form [_] fragment-spread*)
      (-form [_] form)
      Serializable
      (-unparse [this opts]
        (oksa.unparse/unparse-fragment-spread
          (merge
            (-get-oksa-opts opts)
            (protocol/-opts this)))))))

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
  (-fragment-spread opts))

(defn -inline-fragment
  [opts selection-set]
  (let [opts (or opts {})
        form (cond-> [:oksa/inline-fragment opts] (some? selection-set) (conj (protocol/-form selection-set)))
        [type opts :as inline-fragment*] (-parse-or-throw :oksa.parse/InlineFragment
                                                          form
                                                          (oksa.parse/-inline-fragment-parser opts)
                                                          "invalid inline fragment parser")]
    (reify
      AST
      (-type [_] type)
      (-opts [_] (update opts
                         :directives
                         (partial oksa.util/transform-malli-ast
                                  oksa.parse/-transform-map)))
      (-parsed-form [_] inline-fragment*)
      (-form [_] form)
      Serializable
      (-unparse [this opts]
        (oksa.unparse/unparse-inline-fragment
          (merge (-get-oksa-opts opts)
                 (protocol/-opts this))
          selection-set)))))

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
   (-inline-fragment opts selection-set)))

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
    (reduce (fn [m itm]
              (cond
                (satisfies? UpdateableOption itm) (update m (protocol/-update-key itm) (protocol/-update-fn itm))
                :else m))
            {}
            vargs*)))

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
  (let [form name
        name* (-parse-or-throw :oksa.parse/Name
                               name
                               oksa.parse/-name-parser
                               "invalid `on`")]
    (reify
      AST
      (-form [_] form)
      (-parsed-form [_] name*)
      (-type [_] :oksa.parse/Name)
      (-opts [_] {})
      UpdateableOption
      (-update-key [_] :on)
      (-update-fn [this] (constantly (protocol/-form this))))))

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
  (let [form name
        name* (-parse-or-throw :oksa.parse/Name
                               name
                               oksa.parse/-name-parser
                               "invalid name")]
    (reify
      AST
      (-form [_] form)
      (-parsed-form [_] name*)
      (-type [_] :oksa.parse/Name)
      (-opts [_] {})
      UpdateableOption
      (-update-key [_] :name)
      (-update-fn [this] (constantly (protocol/-form this))))))

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
  (let [form name
        alias* (-parse-or-throw :oksa.parse/Alias
                                form
                                oksa.parse/-alias-parser
                                "invalid alias")]
    (reify
      AST
      (-form [_] form)
      (-parsed-form [_] alias*)
      (-type [_] :oksa.parse/Alias)
      (-opts [_] {})
      UpdateableOption
      (-update-key [_] :alias)
      (-update-fn [this] (constantly (protocol/-form this))))))

(defn -directive-name
  [directive-name]
  (let [directive-name* (-parse-or-throw :oksa.parse/DirectiveName
                                         directive-name
                                         oksa.parse/-directive-name-parser
                                         "invalid directive name")]
    (reify
      AST
      (-type [_] :oksa.parse/DirectiveName)
      (-opts [_] {})
      (-parsed-form [_] directive-name*)
      (-form [_] directive-name)
      Serializable
      (-unparse [_ _opts] (clojure.core/name directive-name*)))))

(def -directives-empty-state [])
(def -arguments-empty-state {})
(def -variables-empty-state [])

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
   (let [opts (if (satisfies? protocol/Argumented arguments)
                {:arguments (protocol/-arguments arguments)}
                (cond-> {} (some? arguments) (assoc :arguments arguments)))
         form [name opts]
         directive* (-parse-or-throw :oksa.parse/Directive
                                     form
                                     oksa.parse/-directive-parser
                                     "invalid directive")]
     (reify
       AST
       (-form [_] form)
       (-parsed-form [_] directive*)
       (-type [_] :oksa.parse/Directive)
       (-opts [_] opts)
       UpdateableOption
       (-update-key [_] :directives)
       (-update-fn [this] #((fnil conj -directives-empty-state) % (protocol/-form this)))))))

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
                                                            (-directive-name x)
                                                            x))))]
    (-validate (and (not-empty directives*)
                    (every? #(or (-directive-name? %)
                                 (-directive? %)) directives*))
               "invalid directives, expected `oksa.alpha.api/directive` or naked directive (keyword)")
    (let [form (mapv protocol/-form directives*)
          [type _directives
           :as directives*] (-parse-or-throw :oksa.parse/Directives
                                             form
                                             oksa.parse/-directives-parser
                                             "invalid directives")]
      (reify
        AST
        (-type [_] type)
        (-form [_] form)
        (-parsed-form [_] directives*)
        (-opts [_] {})
        UpdateableOption
        (-update-key [_] :directives)
        (-update-fn [this] #((fnil into -directives-empty-state) % (protocol/-form this)))))))

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
  (let [form {name value}
        argument* (-parse-or-throw :oksa.parse/Arguments
                                   form
                                   oksa.parse/-arguments-parser
                                   "invalid argument")]
    (reify
      AST
      (-form [_] form)
      (-parsed-form [_] argument*)
      (-type [_] :oksa.parse/Arguments)
      (-opts [_] {})
      UpdateableOption
      (-update-key [_] :arguments)
      (-update-fn [this] #(merge % (protocol/-form this)))
      Argumented
      (-arguments [this] (protocol/-form this)))))

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
  (let [form (->> arguments
                  (partition 2)
                  (map vec)
                  (into {}))
        arguments* (-parse-or-throw :oksa.parse/Arguments
                                    form
                                    oksa.parse/-arguments-parser
                                    "invalid arguments")]
    (reify
      AST
      (-type [_] :oksa.parse/Arguments)
      (-form [_] form)
      (-parsed-form [_] arguments*)
      (-opts [_] {})
      UpdateableOption
      (-update-key [_] :arguments)
      (-update-fn [this] #(merge % (protocol/-form this)))
      Argumented
      (-arguments [this] (protocol/-form this)))))

(defn type
  "Returns a named type using `type-name`.

  See also [NamedType](https://spec.graphql.org/October2021/#NamedType)."
  [type-name]
  (let [form type-name
        type* (-parse-or-throw :oksa.parse/TypeName
                               form
                               oksa.parse/-type-name-parser
                               "invalid type")]
    (reify
      AST
      (-type [_] :oksa.parse/TypeName)
      (-form [_] form)
      (-parsed-form [_] type*)
      (-opts [_] {})
      Serializable
      (-unparse [this _opts] (clojure.core/name (protocol/-parsed-form this))))))

(defn type!
  "Returns a non-nil named type using `type-name`.

  See also [NonNullType](https://spec.graphql.org/October2021/#NonNullType)."
  [type-name]
  (let [form [type-name {:non-null true}]
        [type-name* _opts :as non-null-type*] (-parse-or-throw :oksa.parse/NamedTypeOrNonNullNamedType
                                                               form
                                                               oksa.parse/-named-type-or-non-null-named-type-parser
                                                               "invalid non-null type")]
    (reify
      AST
      (-type [_] :oksa.parse/NamedTypeOrNonNullNamedType)
      (-form [_] form)
      (-parsed-form [_] non-null-type*)
      (-opts [_] {})
      Serializable
      (-unparse [_ _opts] (str (clojure.core/name type-name*) "!")))))

(defn -list
  [opts type-or-list]
  (-validate (or (-type? type-or-list) (-list? type-or-list))
             "invalid type given, expected `oksa.alpha.api/type`, `oksa.alpha.api/type!`, keyword (naked type), `oksa.alpha.api/list`, or `oksa.alpha.api/list!`")
  (let [type-or-list* (if (or (keyword? type-or-list) (string? type-or-list))
                        (type type-or-list)
                        type-or-list)
        form [:oksa/list opts (protocol/-form type-or-list*)]
        list* (-parse-or-throw :oksa.parse/ListTypeOrNonNullListType
                               form
                               oksa.parse/-list-type-or-non-null-list-type-parser
                               "invalid list")]
    (reify
      AST
      (-type [_] :oksa.parse/ListTypeOrNonNullListType)
      (-form [_] form)
      (-parsed-form [_] list*)
      (-opts [_] opts)
      Serializable
      (-unparse [this _opts]
        (oksa.unparse/-format-list type-or-list*
                                   (merge (-get-oksa-opts opts)
                                          (protocol/-opts this)))))))

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
  (-list {:non-null false} type-or-list))

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
  (-list {:non-null true} type-or-list))

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
   (let [variable-type* (if (or (keyword? variable-type) (string? variable-type))
                          (type variable-type)
                          variable-type)
         form (cond-> [variable-name]
                (some? opts) (conj opts)
                true (conj (protocol/-form variable-type*)))
         variable* (-parse-or-throw :oksa.parse/VariableDefinitions
                                    form
                                    oksa.parse/-variable-definitions-parser
                                    "invalid variable definitions")]
     (reify
       AST
       (-type [_] :oksa.parse/VariableDefinitions)
       (-form [_] form)
       (-parsed-form [_] variable*)
       (-opts [_] (update opts :directives (partial oksa.util/transform-malli-ast oksa.parse/-transform-map)))
       UpdateableOption
       (-update-key [_] :variables)
       (-update-fn [this] #((fnil into -variables-empty-state) % (protocol/-form this)))))))

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
    (let [form (->> variable-definitions*
                    (reduce (fn [acc [variable-name variable-type]]
                              (let [variable-type* (if (or (keyword? variable-type) (string? variable-type))
                                                     (type variable-type)
                                                     variable-type)]
                                (into acc [variable-name (protocol/-form variable-type*)])))
                            []))
          variables* (-parse-or-throw :oksa.parse/VariableDefinitions
                                      form
                                      oksa.parse/-variable-definitions-parser
                                      "invalid variable definitions")]
      (reify
        AST
        (-type [_] :oksa.parse/VariableDefinitions)
        (-form [_] form)
        (-parsed-form [_] variables*)
        (-opts [_] (update opts :directives (partial oksa.util/transform-malli-ast oksa.parse/-transform-map)))
        UpdateableOption
        (-update-key [_] :variables)
        (-update-fn [this] #((fnil into -variables-empty-state) % (protocol/-form this)))))))

(defn default
  "Returns default `value` under `:default` key. Used directly within `oksa.alpha.api/opts`.

  Example:

  ```
  (opts (variable :fooVar (opts (default 123)) :Foo))
  ; => {:variables [:fooVar {:default 123} :Foo]}
  ```

  See also [DefaultValue](https://spec.graphql.org/October2021/#DefaultValue)."
  [value]
  (let [value* (-parse-or-throw :oksa.parse/Value
                                value
                                oksa.parse/-value-parser
                                "invalid value")]
    (reify
      AST
      (-type [_] :oksa.parse/Value)
      (-form [_] value)
      (-parsed-form [_] value*)
      (-opts [_] {})
      UpdateableOption
      (-update-key [_] :default)
      (-update-fn [_] (constantly value*)))))

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

;;
;;
;; WIP
;;
;;

(def -transform-map
  (letfn [(operation [operation-type opts xs]
            (into [operation-type (-> opts
                                      (update :directives (partial oksa.util/transform-malli-ast -transform-map))
                                      (update :variables (partial oksa.util/transform-malli-ast -transform-map)))]
                  xs))
          (document [opts xs]
            (into [:document opts] xs))
          (fragment [opts xs]
            (assert (some? (:name opts)) "missing name")
            (into [:fragment (update opts
                                     :directives
                                     (partial oksa.util/transform-malli-ast -transform-map))]
                  xs))
          (fragment-spread [opts]
            (assert (some? (:name opts)) "missing name")
            (-fragment-spread opts)
            #_(into [:fragment-spread
                   (update opts
                           :directives
                           (partial oksa.util/transform-malli-ast -transform-map))]
                  xs))
          (inline-fragment [opts xs]
            (assert (not (some? (:name opts))) "inline fragments can't have name")
            (-inline-fragment opts xs)
            #_(into [:inline-fragment (update opts
                                            :directives
                                            (partial oksa.util/transform-malli-ast -transform-map))]
                  xs))
          (selection-set [xs]
            (-selection-set nil (mapcat (fn [{:oksa.parse/keys [node children] :as x}]
                                          (let [[selection-type value] node]
                                            (cond-> (into []
                                                          [(case selection-type
                                                             :oksa.parse/NakedField (oksa.util/transform-malli-ast -transform-map [:oksa.parse/Field [value {}]])
                                                             :oksa.parse/WrappedField (oksa.util/transform-malli-ast -transform-map value)
                                                             :oksa.parse/FragmentSpread (oksa.util/transform-malli-ast -transform-map value)
                                                             :oksa.parse/InlineFragment (oksa.util/transform-malli-ast -transform-map value))])
                                                    (some? children) (into [(oksa.util/transform-malli-ast -transform-map children)]))))
                                        xs))
            #_(into [:selectionset {}]
                  (map (fn [{:oksa.parse/keys [node children] :as x}]
                         (let [[selection-type value] node]
                           (cond-> (into [:selection {}]
                                         [(case selection-type
                                            :oksa.parse/NakedField (oksa.util/transform-malli-ast -transform-map [:oksa.parse/Field [value {}]])
                                            :oksa.parse/WrappedField (oksa.util/transform-malli-ast -transform-map value)
                                            :oksa.parse/FragmentSpread (oksa.util/transform-malli-ast -transform-map value)
                                            :oksa.parse/InlineFragment (oksa.util/transform-malli-ast -transform-map value))])
                                   (some? children) (into [(oksa.util/transform-malli-ast -transform-map children)]))))
                       xs)))]
    {:oksa/document document
     :<> document
     :oksa/fragment fragment
     :# fragment
     :oksa/query (partial operation :query)
     :oksa/mutation (partial operation :mutation)
     :oksa/subscription (partial operation :subscription)
     :... (fn fragment-dispatcher
            ([opts]
             (fragment-dispatcher opts []))
            ([opts xs]
             (if (some? (:name opts))
               (fragment-spread opts)
               (apply inline-fragment opts xs))))
     :oksa/fragment-spread fragment-spread
     :oksa/inline-fragment inline-fragment
     :oksa.parse/SelectionSet selection-set
     :oksa.parse/Field (fn [[name opts xs]]
                         (-field name opts xs)
                         #_(into [:selection {} [:field (merge (update opts
                                                                       :directives
                                                                       (partial oksa.util/transform-malli-ast -transform-map))
                                                               {:name name})]]
                                 (filterv some? xs)))
     :oksa.parse/Directives (partial into [])
     :oksa.parse/Directive (fn [[name opts]] [name opts])
     :oksa.parse/DirectiveName (fn [directive-name] [directive-name {}])
     :oksa.parse/VariableDefinitions (fn [xs]
                                       (map (fn [[variable-name opts type :as _variable-definition]]
                                              [variable-name
                                               (update opts :directives (partial oksa.util/transform-malli-ast -transform-map))
                                               type])
                                            xs))
     :oksa.parse/TypeName (fn [type-name] [type-name {}])
     :oksa.parse/NamedTypeOrNonNullNamedType identity
     :oksa.parse/ListTypeOrNonNullListType identity
     :oksa.parse/AbbreviatedListType (fn [x] (into [:oksa/list {}] x))
     :oksa.parse/AbbreviatedNonNullListType (fn [[_ name-or-list]] (into [:oksa/list {:non-null true}] [name-or-list]))}))

(defn- parse
  [x]
  (let [parsed (oksa.parse/-graphql-dsl-parser x)]
    (if (not= :malli.core/invalid parsed)
      parsed
      (throw (ex-info "invalid form" {})))))

(defn- xf
  [ast]
  (oksa.util/transform-malli-ast -transform-map ast))

(defn to-ast
  [x]
  (-> (parse x)
      (xf)))

;;
;;
;;
;;
;;
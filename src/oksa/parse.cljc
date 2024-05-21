(ns oksa.parse
  (:require [malli.core :as m]
            [oksa.util :as util]
            [oksa.alpha.protocol :refer [Argumented
                                         AST
                                         Serializable
                                         Representable
                                         UpdateableOption]
             :as protocol]
            [oksa.unparse])
  (:refer-clojure :exclude [name type]))

#?(:cljs (goog-define mode "default")
   :clj  (def ^{:doc "Modes `default` and `debug` supported."}
           mode (as-> (or (System/getProperty "oksa.api/mode") "default") $ (.intern $))))

(def -directives-empty-state [])
(def -variables-empty-state [])

(declare -transform-map)

(def -name-pattern "[_A-Za-z][_0-9A-Za-z]*")
(def re-name (re-pattern -name-pattern))
(def re-variable-name (re-pattern (str "[$]?" -name-pattern)))
(def re-type-name (re-pattern (str -name-pattern "[!]?")))
(def re-fragment-name (re-pattern (str "(?!on)" -name-pattern)))
(def re-variable-reference (re-pattern (str "[$]" -name-pattern)))
(def re-enum-value (re-pattern (str "(?!(true|false|null))" -name-pattern)))

(def ^:private reserved-keywords
  (delay (set (into (filter #(some-> % namespace (= "oksa")) (keys -transform-map))
                    #{:<> :# :...}))))

(defn ^:private registry
  [opts]
  {::Document [:or
               [:schema [:ref ::Definition]]
               [:cat
                [:enum :oksa/document :<>]
                [:? :map]
                [:+ [:schema [:ref ::Definition]]]]]
   ::Definition [:schema [:ref ::ExecutableDefinition]]
   ::ExecutableDefinition [:or
                           [:schema [:ref ::OperationDefinition]]
                           [:schema [:ref ::FragmentDefinition]]]
   ::FragmentDefinition [:cat
                         [:enum :oksa/fragment :#]
                         [:map
                          [:name {:optional false}
                           [:ref ::FragmentName]]
                          [:on [:ref ::Name]]
                          [:directives {:optional true}
                           [:schema [:ref ::Directives]]]]
                         [:repeat {:max 1} [:schema [:ref ::SelectionSet]]]]
   ::OperationDefinition [:or
                          [:cat
                           [:enum :oksa/query :oksa/mutation :oksa/subscription]
                           [:? [:map
                                [:name {:optional true} [:ref ::Name]]
                                [:variables {:optional true}
                                 [:schema [:ref ::VariableDefinitions]]]
                                [:directives {:optional true}
                                 [:schema [:ref ::Directives]]]]]
                           [:repeat {:max 1} [:schema [:ref ::SelectionSet]]]]
                          [:schema [:ref ::SelectionSet]]]
   ::VariableDefinitions [:orn [::VariableDefinitions
                                [:+ [:cat
                                     [:schema [:ref ::VariableName]]
                                     [:? [:map
                                          [:directives {:optional true}
                                           [:schema [:ref ::Directives]]]
                                          [:default {:optional true}
                                           [:schema [:ref ::Value]]]]]
                                     [:schema [:ref ::Type]]]]]]
   ::Type [:orn
           [::TypeName [:schema [:schema [:ref ::TypeName]]]]
           [::NamedTypeOrNonNullNamedType [:schema [:ref ::NamedTypeOrNonNullNamedType]]]
           [::ListTypeOrNonNullListType [:schema [:ref ::ListTypeOrNonNullListType]]]
           [::AbbreviatedListType [:schema [:ref ::ListType]]]
           [::AbbreviatedNonNullListType [:schema [:ref ::NonNullListType]]]]
   ::TypeName (if (:oksa/strict opts)
                [:and
                 [:not [:enum :oksa/list]]
                 [:or :keyword :string]
                 [:fn {:error/message (str "invalid character range for name, should follow the pattern: " re-type-name)}
                  (fn [x] (re-matches re-type-name (clojure.core/name x)))]]
                [:and
                 [:not [:enum :oksa/list]]
                 [:or :keyword :string]])
   ::TypeOpts [:map
               [:non-null :boolean]]
   ::NamedTypeOrNonNullNamedType [:cat
                                  [:schema [:ref ::TypeName]]
                                  [:schema [:ref ::TypeOpts]]]
   ::ListType [:cat [:schema [:ref ::Type]]]
   ::NonNullListType [:cat [:= :!] [:schema [:ref ::Type]]]
   ::ListTypeOrNonNullListType [:cat
                                [:= :oksa/list]
                                [:? [:schema [:ref ::TypeOpts]]]
                                [:schema [:ref ::Type]]]
   ::SelectionSet [:orn
                   [::SelectionSet [:+ [:alt
                                        [:catn
                                         [::node [:schema [:ref ::Selection]]]
                                         [::children [:? [:schema [:ref ::SelectionSet]]]]]
                                        [:catn
                                         [::node [:schema [:ref ::WrappedField]]]]
                                        ;; Special case where subsequent selection set is allowed
                                        [:catn
                                         [::node [:schema [:ref ::BareField]]]
                                         [::children [:? [:schema [:ref ::SelectionSet]]]]]]]]]
   ::WrappedField [:orn [::WrappedField [:schema [:ref ::Field]]]]
   ::Selection [:orn
                [::FragmentSpread [:schema [:ref ::FragmentSpread]]]
                [::InlineFragment [:schema [:ref ::InlineFragment]]]
                [::NakedField [:schema [:ref ::NakedField]]]]
   ::Field [:orn [::Field [:cat
                           [:schema [:ref ::FieldName]]
                           [:map
                            [:alias {:optional true} [:ref ::Alias]]
                            [:arguments {:optional true}
                             [:ref ::Arguments]]
                            [:directives {:optional true}
                             [:ref ::Directives]]]
                           [:? [:schema [:ref ::SelectionSet]]]]]]
   ::BareField [:orn [::Field [:cat
                           [:schema [:ref ::FieldName]]
                           [:map
                            [:alias {:optional true} [:ref ::Alias]]
                            [:arguments {:optional true}
                             [:ref ::Arguments]]
                            [:directives {:optional true}
                             [:ref ::Directives]]]]]]
   ::NakedField [:schema [:ref ::FieldName]]
   ::FieldName [:and
                [:schema [:ref ::Name]]
                [:fn #(not (@reserved-keywords %))]]
   ::FragmentSpread [:cat
                     [:enum :oksa/fragment-spread :...]
                     [:? [:map
                          [:name {:optional false}
                           [:ref ::FragmentName]]
                          [:directives {:optional true}
                           [:ref ::Directives]]]]]
   ::InlineFragment [:cat
                     [:enum :oksa/inline-fragment :...]
                     [:? [:map
                          [:directives {:optional true}
                           [:ref ::Directives]]
                          [:on {:optional true}
                           [:ref ::Name]]]]
                     [:repeat {:max 1} [:schema [:ref ::SelectionSet]]]]
   ::Alias [:schema [:ref ::Name]]
   ::Name (if (:oksa/strict opts)
            [:and [:or :keyword :string]
             [:fn {:error/message (str "invalid character range for name, should follow the pattern: " re-name)}
              (fn [x] (re-matches re-name (clojure.core/name x)))]]
            [:or :keyword :string])
   ::VariableName (if (:oksa/strict opts)
                    [:and [:or :keyword :string]
                     [:fn {:error/message (str "invalid character range for variable name, should follow the pattern: " re-variable-name)}
                      (fn [x] (re-matches re-variable-name (clojure.core/name x)))]]
                    [:or :keyword :string])
   ::FragmentName (if (:oksa/strict opts)
                    [:and [:or :keyword :string]
                     [:fn {:error/message (str "invalid character range for fragment name, should follow the pattern: " re-fragment-name)}
                      (fn [x] (re-matches re-fragment-name (clojure.core/name x)))]]
                    [:or :keyword :string])
   ::Value [:or
            number?
            :string
            :boolean
            :nil
            (if (:oksa/strict opts)
              [:and :keyword
               [:fn
                {:error/message (str "invalid character range for value, should follow either: " re-enum-value ", or: " re-variable-reference)}
                (fn [x]
                  (let [s (clojure.core/name x)]
                    (or (re-matches re-variable-reference s)
                        (re-matches re-enum-value s))))]]
              :keyword)
            coll?
            :map]
   ::Arguments [:map-of [:ref ::Name] [:ref ::Value]]
   ::Directives [:orn [::Directives [:+ [:orn
                                         [::DirectiveName [:schema [:ref ::DirectiveName]]]
                                         [::Directive [:schema [:ref ::Directive]]]]]]]
   ::Directive [:cat
                [:schema [:ref ::DirectiveName]]
                [:? [:map
                     [:arguments {:optional true}
                      [:ref ::Arguments]]]]]
   ::DirectiveName [:ref ::Name]})

(defn -graphql-dsl-lang
  ([schema]
   [:schema {:registry (registry nil)} schema])
  ([opts schema]
   [:schema {:registry (registry opts)} schema]))

(def -graphql-dsl-parser (m/parser (-graphql-dsl-lang ::Document)))
(def -field-parser (m/parser (-graphql-dsl-lang ::Field)))
(def -fragment-spread-parser (m/parser (-graphql-dsl-lang ::FragmentSpread)))
(def -inline-fragment-parser (m/parser (-graphql-dsl-lang ::InlineFragment)))
(def -naked-field-parser (m/parser (-graphql-dsl-lang ::NakedField)))
(def -selection-set-parser (m/parser (-graphql-dsl-lang ::SelectionSet)))
(def -operation-definition-parser (m/parser (-graphql-dsl-lang ::OperationDefinition)))
(def -fragment-definition-parser (m/parser (-graphql-dsl-lang ::FragmentDefinition)))
(def -directives-parser (m/parser (-graphql-dsl-lang ::Directives)))
(def -directive-parser (m/parser (-graphql-dsl-lang ::Directive)))
(def -directive-name-parser (m/parser (-graphql-dsl-lang ::DirectiveName)))
(def -arguments-parser (m/parser (-graphql-dsl-lang ::Arguments)))
(def -alias-parser (m/parser (-graphql-dsl-lang ::Alias)))
(def -alias-parser-strict (m/parser (-graphql-dsl-lang {:oksa/strict true} ::Alias)))
(def -name-parser (m/parser (-graphql-dsl-lang ::Name)))
(def -name-parser-strict (m/parser (-graphql-dsl-lang {:oksa/strict true} ::Name)))
(def -value-parser (m/parser (-graphql-dsl-lang ::Value)))
(def -value-parser-strict (m/parser (-graphql-dsl-lang {:oksa/strict true} ::Value)))
(def -type-name-parser (m/parser (-graphql-dsl-lang ::TypeName)))
(def -type-name-parser-strict (m/parser (-graphql-dsl-lang {:oksa/strict true} ::TypeName)))
(def -named-type-or-non-null-named-type-parser (m/parser (-graphql-dsl-lang ::NamedTypeOrNonNullNamedType)))
(def -list-type-or-non-null-list-type-parser (m/parser (-graphql-dsl-lang ::ListTypeOrNonNullListType)))
(def -variable-definitions-parser (m/parser (-graphql-dsl-lang ::VariableDefinitions)))
(def -variable-name-parser-strict (m/parser (-graphql-dsl-lang {:oksa/strict true} ::VariableName)))

(defn -parse-or-throw
  [type form parser message]
  (let [retval (parser form)]
    (if (not= retval :malli.core/invalid)
      retval
      (throw (ex-info message
                      (cond
                        (= mode "debug") {:malli.core/explain (malli.core/explain
                                                                (-graphql-dsl-lang type)
                                                                form)
                                          :value form}
                        (= mode "default") {}
                        :else (throw (ex-info "incorrect `oksa.api/mode` (system property), expected one of `default` or `debug`" {:mode mode}))))))))

(def -validate -parse-or-throw)

(defn -get-oksa-opts
  [opts]
  (into {} (filter #(= (namespace (first %)) "oksa") opts)))

(defn -create-document-form
  [definitions]
  (into [:oksa/document {}] (map protocol/-form definitions)))

(defn -create-document
  [opts definitions]
  (reify
    AST
    (-type [_] :oksa/document)
    (-opts [_] opts)
    (-form [_] (-create-document-form definitions))
    Serializable
    (-unparse [_ opts]
      (oksa.unparse/unparse-document opts definitions))
    Representable
    (-gql [this opts] (protocol/-unparse this (merge (-get-oksa-opts opts)
                                                     (protocol/-opts this))))))

(defn -document
  [definitions]
  (let [[_ opts _] (oksa.parse/-parse-or-throw :oksa.parse/Document
                                               (-create-document-form definitions)
                                               oksa.parse/-graphql-dsl-parser
                                               "invalid document")]
    (-create-document opts definitions)))

(defn- parse
  [x]
  (let [parsed (-graphql-dsl-parser x)]
    (if (not= :malli.core/invalid parsed)
      parsed
      (throw (ex-info "invalid form" {})))))

(defn -operation-definition-form
  [operation-type opts selection-set]
  [operation-type (or opts {}) (protocol/-form selection-set)])

(declare -name)

(defn -create-operation-definition
  [type form opts selection-set]
  (reify
    AST
    (-type [_] type)
    (-opts [_] (cond-> opts
                 true (update :directives (partial oksa.util/transform-malli-ast -transform-map))
                 true (update :variables (partial oksa.util/transform-malli-ast -transform-map))
                 (:name opts) (update :name -name)))
    (-form [_] form)
    Serializable
    (-unparse [this opts]
      (oksa.unparse/unparse-operation-definition
        (clojure.core/name (protocol/-type this))
        (merge (-get-oksa-opts opts) (protocol/-opts this))
        selection-set))
    Representable
    (-gql [this opts] (protocol/-unparse this (merge (-get-oksa-opts opts)
                                                     (protocol/-opts this))))))

(defn -operation-definition
  [operation-type opts selection-set]
  (let [opts (or opts {})
        [_ opts* _] (oksa.parse/-parse-or-throw :oksa.parse/OperationDefinition
                                                (-operation-definition-form operation-type opts selection-set)
                                                oksa.parse/-operation-definition-parser
                                                "invalid operation definition")]
    (-create-operation-definition operation-type
                                  (-operation-definition-form operation-type opts selection-set)
                                  opts*
                                  selection-set)))

(declare -on)

(defn -fragment-form
  [opts selection-set]
  [:oksa/fragment opts (protocol/-form selection-set)])

(defn -create-fragment
  [opts form selection-set]
  (reify
    AST
    (-type [_] :oksa/fragment)
    (-opts [_]
      (cond-> (update opts :directives (partial oksa.util/transform-malli-ast -transform-map))
        (:name opts) (update :name -name)
        (:on opts) (update :on -on)))
    (-form [_] form)
    Serializable
    (-unparse [this opts]
      (oksa.unparse/unparse-fragment-definition
        (merge (-get-oksa-opts opts) (protocol/-opts this))
        selection-set))
    Representable
    (-gql [this opts] (protocol/-unparse this (merge (-get-oksa-opts opts)
                                                     (protocol/-opts this))))))

(defn -fragment
  [opts selection-set]
  (let [form (-fragment-form opts selection-set)
        [_ opts* _] (oksa.parse/-parse-or-throw :oksa.parse/FragmentDefinition
                                                form
                                                oksa.parse/-fragment-definition-parser
                                                "invalid fragment definition")]
    (-create-fragment opts* form selection-set)))

(defn -selection-set-form
  [selections]
  (mapv protocol/-form selections))

(defn -create-selection-set
  [selections]
  (reify
    AST
    (-type [_] :oksa.parse/SelectionSet)
    (-opts [_] {})
    (-form [_] (-selection-set-form selections))
    Serializable
    (-unparse [this opts]
      (oksa.unparse/unparse-selection-set (merge (-get-oksa-opts opts)
                                                 (protocol/-opts this))
                                          selections))
    Representable
    (-gql [this opts] (protocol/-unparse this
                                         (merge (-get-oksa-opts opts)
                                                (protocol/-opts this))))))

(defn -selection-set
  [selections]
  (let [form (-selection-set-form selections)]
    (oksa.parse/-validate :oksa.parse/SelectionSet
                          form
                          oksa.parse/-selection-set-parser
                          "invalid selection-set")
    (-create-selection-set selections)))

(declare -arguments)

(defn -field-form
  [name opts selection-set]
  (cond-> [name opts]
    (some? selection-set) (conj (protocol/-form selection-set))))

(declare -alias)

(defn -create-field
  [name form opts selection-set]
  (reify
    AST
    (-type [_] :oksa.parse/Field)
    (-opts [_]
      (cond-> (update opts :directives (partial oksa.util/transform-malli-ast -transform-map))
        (:arguments opts) (update :arguments -arguments)
        (:alias opts) (update :alias -alias)))
    (-form [_] form)
    Serializable
    (-unparse [this opts]
      (let [opts* (merge (-get-oksa-opts opts)
                         (protocol/-opts this))
            f (or (:oksa/field-fn opts*)
                  (:oksa/name-fn opts*))]
        (oksa.unparse/unparse-field (-parse-or-throw :oksa.parse/Name
                                                     (if f
                                                       (f name)
                                                       name)
                                                     oksa.parse/-name-parser-strict
                                                     "invalid naked field")
                                    opts*
                                    selection-set)))))

(defn -field
  [name opts selection-set]
  (let [opts (or opts {})
        form (-field-form name opts selection-set)
        [_ [_ opts* _]] (oksa.parse/-parse-or-throw :oksa.parse/Field
                                                    form
                                                    oksa.parse/-field-parser
                                                    "invalid field")]
    (-create-field name (-field-form name opts selection-set) opts* selection-set)))

(defn -naked-field
  [name]
  (let [naked-field* (oksa.parse/-parse-or-throw :oksa.parse/NakedField
                                                 name
                                                 oksa.parse/-naked-field-parser
                                                 "invalid naked field")]
    (reify
      AST
      (-type [_] :oksa.parse/NakedField)
      (-opts [_] {})
      (-form [_] naked-field*)
      Serializable
      (-unparse [_ opts]
        (let [f (or (:oksa/field-fn opts)
                    (:oksa/name-fn opts))]
          (oksa.parse/-parse-or-throw :oksa.parse/Name
                                      (clojure.core/name (if f
                                                           (f naked-field*)
                                                           naked-field*))
                                      oksa.parse/-name-parser-strict
                                      "invalid naked field"))))))

(defn -fragment-spread-form
  [opts]
  [:oksa/fragment-spread opts])

(defn -create-fragment-spread
  [opts form]
  (reify
    AST
    (-type [_] :oksa/fragment-spread)
    (-opts [_]
      (update opts
              :directives
              (partial oksa.util/transform-malli-ast
                       -transform-map)))
    (-form [_] form)
    Serializable
    (-unparse [this opts]
      (let [opts* (merge
                    (-get-oksa-opts opts)
                    (protocol/-opts this))
            f (or (:oksa/field-fn opts*)
                  (:oksa/name-fn opts*))]
        (oksa.unparse/unparse-fragment-spread
          opts*
          (-parse-or-throw :oksa.parse/Name
                           (if f
                             (f (:name opts*))
                             (:name opts*))
                           oksa.parse/-name-parser-strict
                           "invalid naked field"))))))

(defn -fragment-spread
  [opts]
  (let [form (-fragment-spread-form opts)
        [_ opts] (oksa.parse/-parse-or-throw :oksa.parse/FragmentSpread
                                             form
                                             oksa.parse/-fragment-spread-parser
                                             "invalid fragment spread parser")]
    (-create-fragment-spread opts form)))

(defn -inline-fragment-form
  [opts selection-set]
  (cond-> [:oksa/inline-fragment opts] (some? selection-set) (conj (protocol/-form selection-set))))

(defn -create-inline-fragment
  [opts form selection-set]
  (reify
    AST
    (-type [_] :oksa/inline-fragment)
    (-opts [_] (cond-> (update opts
                               :directives
                               (partial oksa.util/transform-malli-ast
                                        -transform-map))
                 (:on opts) (update :on -on)))
    (-form [_] form)
    Serializable
    (-unparse [this opts]
      (oksa.unparse/unparse-inline-fragment
        (merge (-get-oksa-opts opts)
               (protocol/-opts this))
        selection-set))))

(defn -inline-fragment
  [opts selection-set]
  (let [opts (or opts {})
        form (-inline-fragment-form opts selection-set)
        [_ opts] (oksa.parse/-parse-or-throw :oksa.parse/InlineFragment
                                             form
                                             oksa.parse/-inline-fragment-parser
                                             "invalid inline fragment parser")]
    (-create-inline-fragment opts form selection-set)))

(defn -opts
  [& options]
  (reduce (fn [m itm]
            (cond
              (satisfies? UpdateableOption itm) (update m (protocol/-update-key itm) (protocol/-update-fn itm))
              :else m))
          {}
          options))

(defn -directive-name
  [directive-name]
  (let [directive-name* (oksa.parse/-parse-or-throw :oksa.parse/DirectiveName
                                                    directive-name
                                                    oksa.parse/-directive-name-parser
                                                    "invalid directive name")]
    (reify
      AST
      (-type [_] :oksa.parse/DirectiveName)
      (-opts [_] {})
      (-form [_] directive-name)
      Serializable
      (-unparse [_ _opts] (clojure.core/name directive-name*)))))

(defn -directives-form
  [directives]
  (mapv protocol/-form directives))

(defn -create-directives
  [form directives]
  (reify
    AST
    (-type [_] :oksa.parse/Directives)
    (-form [_] form)
    (-opts [_] {})
    UpdateableOption
    (-update-key [_] :directives)
    (-update-fn [this] #((fnil into -directives-empty-state) % (protocol/-form this)))
    Serializable
    (-unparse [this opts]
      (oksa.unparse/format-directives (merge (-get-oksa-opts opts)
                                             (protocol/-opts this)) directives))))

(defn -directives
  [directives]
  (let [form (-directives-form directives)]
    (oksa.parse/-validate :oksa.parse/Directives
                          form
                          oksa.parse/-directives-parser
                          "invalid directives")
    (-create-directives form directives)))

(declare -type)

(defn -coerce-variable-type
  ([variable-type]
   (-coerce-variable-type nil variable-type))
  ([opts variable-type]
   (if (or (keyword? variable-type) (string? variable-type))
     (-type opts variable-type)
     variable-type)))

(defn -list-form
  [opts type-or-list]
  [:oksa/list opts (protocol/-form type-or-list)])

(defn -create-list
  [opts form type-or-list]
  (reify
    AST
    (-type [_] :oksa.parse/ListTypeOrNonNullListType)
    (-form [_] form)
    (-opts [_] opts)
    Serializable
    (-unparse [this _opts]
      (oksa.unparse/-format-list type-or-list
                                 (merge (-get-oksa-opts opts)
                                        (protocol/-opts this))))))

(defn -list
  [opts type-or-list]
  (let [type-or-list* (-coerce-variable-type type-or-list)
        form (-list-form opts type-or-list*)]
    (oksa.parse/-validate :oksa.parse/ListTypeOrNonNullListType
                          form
                          oksa.parse/-list-type-or-non-null-list-type-parser
                          "invalid list")
    (-create-list opts form type-or-list*)))

(defn -create-type
  [opts type]
  (reify
    AST
    (-type [_] :oksa.parse/TypeName)
    (-form [_] type)
    (-opts [_] opts)
    Serializable
    (-unparse [this opts]
      (let [opts (merge (-get-oksa-opts opts)
                        (protocol/-opts this))
            f (or (:oksa/type-fn opts)
                  (:oksa/name-fn opts))]
        (clojure.core/name
          (-parse-or-throw :oksa.parse/TypeName
                           (clojure.core/name (if f
                                                (f type)
                                                type))
                           oksa.parse/-type-name-parser-strict
                           "invalid type name"))))))

(defn -type
  ([type-name]
   (-type nil type-name))
  ([opts type-name]
   (let [type* (oksa.parse/-parse-or-throw :oksa.parse/TypeName
                                           type-name
                                           oksa.parse/-type-name-parser
                                           "invalid type name")]
     (-create-type opts type*))))

(defn -type!-form
  [type-name]
  [type-name {:non-null true}])

(defn -create-type!
  [type-name]
  (reify
    AST
    (-type [_] :oksa.parse/NamedTypeOrNonNullNamedType)
    (-form [_] (-type!-form type-name))
    (-opts [_] {})
    Serializable
    (-unparse [_ _opts] (str (clojure.core/name type-name) "!"))))

(defn -type!
  [type-name]
  (let [form (-type!-form type-name)
        [type-name* _] (oksa.parse/-parse-or-throw :oksa.parse/NamedTypeOrNonNullNamedType
                                                   form
                                                   oksa.parse/-named-type-or-non-null-named-type-parser
                                                   "invalid non-null type")]
    (-create-type! type-name*)))

(declare -default)

(defn -create-variable
  [variable-name opts form variable-type]
  (reify
    AST
    (-type [_] :oksa.parse/VariableDefinitions)
    (-form [_] form)
    (-opts [_]
      (cond-> (update opts :directives (partial oksa.util/transform-malli-ast -transform-map))
        (contains? opts :default) (update :default -default)))
    UpdateableOption
    (-update-key [_] :variables)
    (-update-fn [this] #((fnil into -variables-empty-state) % (protocol/-form this)))
    Serializable
    (-unparse [this opts]
      (let [name-fn (:oksa/name-fn opts)]
        (oksa.unparse/-format-variable-definition
          (-parse-or-throw :oksa.parse/VariableName
                           (clojure.core/name (if name-fn
                                                (name-fn variable-name)
                                                variable-name))
                           oksa.parse/-variable-name-parser-strict
                           "invalid variable name")
          (merge
            (-get-oksa-opts opts)
            (protocol/-opts this))
          variable-type)))))

(defn -variable-form
  [variable-name opts variable-type]
  (cond-> [variable-name]
    (some? opts) (conj opts)
    true (conj (protocol/-form variable-type))))

(defn -variable
  [variable-name opts variable-type]
  (let [variable-type* (-coerce-variable-type opts variable-type)
        form (-variable-form variable-name opts variable-type*)
        [_ [[_ opts* _]]] (oksa.parse/-parse-or-throw :oksa.parse/VariableDefinitions
                                                      form
                                                      oksa.parse/-variable-definitions-parser
                                                      "invalid variable definitions")]
    (-create-variable variable-name opts* form variable-type*)))

(defn -variables
  [variable-definitions]
  (let [form (->> variable-definitions
                  (reduce (fn [acc [variable-name variable-type]]
                            (let [variable-type* (-coerce-variable-type variable-type)]
                              (into acc [variable-name (protocol/-form variable-type*)])))
                          []))]
    (oksa.parse/-validate :oksa.parse/VariableDefinitions
                          form
                          oksa.parse/-variable-definitions-parser
                          "invalid variable definitions")
    (reify
      AST
      (-type [_] :oksa.parse/VariableDefinitions)
      (-form [_] form)
      (-opts [_] {})
      UpdateableOption
      (-update-key [_] :variables)
      (-update-fn [this] #((fnil into oksa.parse/-variables-empty-state) % (protocol/-form this))))))

(defn -directive-opts
  [arguments]
  (if (satisfies? protocol/Argumented arguments)
    {:arguments (protocol/-arguments arguments)}
    (cond-> {} (not-empty arguments) (assoc :arguments arguments))))

(defn -directive-form
  [name opts]
  [name opts])

(defn -create-directive
  [opts form directive-name]
  (reify
    AST
    (-form [_] form)
    (-type [_] :oksa.parse/Directive)
    (-opts [_] (cond-> opts (:arguments opts) (update :arguments -arguments)))
    UpdateableOption
    (-update-key [_] :directives)
    (-update-fn [this] #((fnil conj -directives-empty-state) % (protocol/-form this)))
    Serializable
    (-unparse [this opts]
      (let [f (or (:oksa/directive-fn opts)
                  (:oksa/name-fn opts))]
        (oksa.unparse/format-directive
          (oksa.parse/-parse-or-throw :oksa.parse/Name
                                      (clojure.core/name (if f
                                                           (f directive-name)
                                                           directive-name))
                                      oksa.parse/-name-parser-strict
                                      "invalid name")
          (merge
            (-get-oksa-opts opts)
            (protocol/-opts this)))))))

(defn -directive
  [name arguments]
  (let [opts (-directive-opts arguments)
        form (-directive-form name opts)
        [directive-name _] (oksa.parse/-parse-or-throw :oksa.parse/Directive
                                                       form
                                                       oksa.parse/-directive-parser
                                                       "invalid directive")]
    (-create-directive opts form directive-name)))

(defn -on
  [name]
  (let [form name
        name* (oksa.parse/-parse-or-throw :oksa.parse/Name
                                          name
                                          oksa.parse/-name-parser
                                          "invalid `on`")]
    (reify
      AST
      (-form [_] form)
      (-type [_] :oksa.parse/Name)
      (-opts [_] {})
      UpdateableOption
      (-update-key [_] :on)
      (-update-fn [this] (constantly (protocol/-form this)))
      Serializable
      (-unparse [_ opts]
        (let [name-fn (:oksa/name-fn opts)]
          (oksa.unparse/format-on
            (oksa.parse/-parse-or-throw :oksa.parse/Name
                                        (clojure.core/name (if name-fn
                                                             (name-fn name*)
                                                             name*))
                                        oksa.parse/-name-parser-strict
                                        "invalid name")))))))

(defn -name
  [name]
  (let [form name
        name* (oksa.parse/-parse-or-throw :oksa.parse/Name
                                          name
                                          oksa.parse/-name-parser
                                          "invalid name")]
    (reify
      AST
      (-form [_] form)
      (-type [_] :oksa.parse/Name)
      (-opts [_] {})
      UpdateableOption
      (-update-key [_] :name)
      (-update-fn [this] (constantly (protocol/-form this)))
      Serializable
      (-unparse [_this opts]
        (let [name-fn (:oksa/name-fn opts)]
          (oksa.parse/-parse-or-throw :oksa.parse/Name
                                      (clojure.core/name (if name-fn
                                                           (name-fn name*)
                                                           name*))
                                      oksa.parse/-name-parser-strict
                                      "invalid name"))))))

(defn -alias
  [name]
  (let [form name
        alias* (oksa.parse/-parse-or-throw :oksa.parse/Alias
                                           form
                                           oksa.parse/-alias-parser
                                           "invalid alias")]
    (reify
      AST
      (-form [_] form)
      (-type [_] :oksa.parse/Alias)
      (-opts [_] {})
      UpdateableOption
      (-update-key [_] :alias)
      (-update-fn [this] (constantly (protocol/-form this)))
      Serializable
      (-unparse [_ opts]
        (let [name-fn (:oksa/name-fn opts)]
          (oksa.unparse/format-alias (oksa.parse/-parse-or-throw :oksa.parse/Alias
                                                                 (clojure.core/name (if name-fn
                                                                                      (name-fn alias*)
                                                                                      alias*))
                                                                 oksa.parse/-alias-parser-strict
                                                                 "invalid naked field")))))))

(defn -argument
  [name value]
  (let [form {name value}]
    (oksa.parse/-validate :oksa.parse/Arguments
                          form
                          oksa.parse/-arguments-parser
                          "invalid argument")
    (reify
      AST
      (-form [_] form)
      (-type [_] :oksa.parse/Arguments)
      (-opts [_] {})
      UpdateableOption
      (-update-key [_] :arguments)
      (-update-fn [this] #(merge % (protocol/-form this)))
      Argumented
      (-arguments [this] (protocol/-form this))
      Serializable
      (-unparse [_ opts]
        (let [name-fn (:oksa/name-fn opts)
              value-fn (or (:oksa/enum-fn opts)
                           (:oksa/name-fn opts))]
          (oksa.unparse/-format-argument
            (oksa.parse/-parse-or-throw :oksa.parse/Name
                                        (if name-fn
                                          (name-fn name)
                                          name)
                                        oksa.parse/-name-parser-strict
                                        "invalid name")
            (oksa.parse/-parse-or-throw :oksa.parse/Value
                                        (if (and (keyword? value) (some? name-fn))
                                          (value-fn value)
                                          value)
                                        oksa.parse/-value-parser-strict
                                        "invalid value")))))))

(defn -arguments
  [arguments]
  (let [form arguments]
    (oksa.parse/-validate :oksa.parse/Arguments
                          form
                          oksa.parse/-arguments-parser
                          "invalid arguments")
    (let [arguments* (map (fn [[argument-name argument-value]] (-argument argument-name argument-value)) form)]
      (reify
        AST
        (-type [_] :oksa.parse/Arguments)
        (-form [_] form)
        (-opts [_] {})
        UpdateableOption
        (-update-key [_] :arguments)
        (-update-fn [this] #(merge % (protocol/-form this)))
        Argumented
        (-arguments [this] (protocol/-form this))
        Serializable
        (-unparse [this opts]
          (oksa.unparse/-format-arguments (merge (-get-oksa-opts opts)
                                                 (protocol/-opts this)) arguments*))))))

(defn -default
  [value]
  (let [value* (oksa.parse/-parse-or-throw :oksa.parse/Value
                                           value
                                           oksa.parse/-value-parser
                                           "invalid value")]
    (reify
      AST
      (-type [_] :oksa.parse/Value)
      (-form [_] value)
      (-opts [_] {})
      UpdateableOption
      (-update-key [_] :default)
      (-update-fn [_] (constantly value*))
      Serializable
      (-unparse [_ opts]
        (let [f (or (:oksa/enum-fn opts)
                    (:oksa/name-fn opts))]
          (oksa.unparse/format-default
            (oksa.parse/-parse-or-throw :oksa.parse/Value
                                        (if (and (keyword? value) (some? f))
                                          (f value)
                                          value)
                                        oksa.parse/-value-parser-strict
                                        "invalid value")))))))

(def -transform-map
  (letfn [(operation [operation-type opts [xs]]
            (-create-operation-definition operation-type
                                          (-operation-definition-form operation-type opts xs)
                                          opts
                                          xs))
          (document [opts definitions]
            (-create-document opts definitions))
          (fragment [{:keys [directives] :as options} [selection-set]]
            (assert (some? (:name options)) "missing name")
            (let [opts (-opts
                         (-name (:name options))
                         (when (:on options) (-on (:on options)))
                         (when directives (oksa.util/transform-malli-ast -transform-map directives)))]
              (-create-fragment options (-fragment-form opts selection-set) selection-set)))
          (fragment-spread [{:keys [directives] :as options}]
            (assert (some? (:name options)) "missing name")
            (let [opts (-opts
                         (-name (:name options))
                         (when directives (oksa.util/transform-malli-ast -transform-map directives)))]
              (-create-fragment-spread options (-fragment-spread-form opts))))
          (inline-fragment [{:keys [directives] :as options} [selection-set]]
            (assert (not (some? (:name options))) "inline fragments can't have name")
            (let [opts (-opts
                         (when (:on options) (-on (:on options)))
                         (when directives (oksa.util/transform-malli-ast -transform-map directives)))]
              (-create-inline-fragment options (-inline-fragment-form opts selection-set) selection-set)))
          (selection-set [xs]
            (-create-selection-set (mapcat (fn [{:oksa.parse/keys [node children]}]
                                             (let [[selection-type value] node]
                                               (cond-> (into []
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
     :oksa/query (partial operation :oksa/query)
     :oksa/mutation (partial operation :oksa/mutation)
     :oksa/subscription (partial operation :oksa/subscription)
     :... (fn fragment-dispatcher
            ([opts]
             (fragment-dispatcher opts []))
            ([opts selection-set]
             (if (some? (:name opts))
               (fragment-spread opts)
               (inline-fragment opts selection-set))))
     :oksa/fragment-spread fragment-spread
     :oksa/inline-fragment inline-fragment
     :oksa.parse/SelectionSet selection-set
     :oksa.parse/Field (fn [[name opts xs]]
                         (-create-field name (-field-form name opts xs) opts xs))
     :oksa.parse/Directives (fn [directives]
                              (-create-directives (-directives-form directives) directives))
     :oksa.parse/Directive (fn [[directive-name opts]]
                             (-create-directive opts (-directive-form directive-name opts) directive-name))
     :oksa.parse/DirectiveName (fn [directive-name]
                                 (-create-directive nil (-directive-form clojure.core/name nil) directive-name))
     :oksa.parse/VariableDefinitions (fn [xs]
                                       (mapv (fn [[variable-name options type :as _variable-definition]]
                                               (-create-variable variable-name
                                                                 options
                                                                 (-variable-form variable-name
                                                                                 options
                                                                                 type)
                                                                 type))
                                             xs))
     :oksa.parse/TypeName (fn [type-name]
                            (-create-type nil type-name))
     :oksa.parse/NamedTypeOrNonNullNamedType (fn [[type-name _]]
                                               (-create-type! type-name))
     :oksa.parse/ListTypeOrNonNullListType (fn [[_ {:keys [non-null]} type]]
                                             (let [opts (if non-null
                                                          {:non-null true}
                                                          {:non-null false})]
                                               (-create-list opts (-list-form opts type) type)))
     :oksa.parse/AbbreviatedListType (fn [[type]]
                                       (let [opts {:non-null false}]
                                         (-create-list opts (-list-form opts type) type)))
     :oksa.parse/AbbreviatedNonNullListType (fn [[_ type-or-list]]
                                              (let [opts {:non-null true}]
                                                (-create-list opts (-list-form opts type-or-list) type-or-list)))}))

(defn- xf
  [ast]
  (util/transform-malli-ast -transform-map ast))

(defn to-ast
  [x]
  (-> (parse x)
      (xf)))

(defn explain
  [x]
  (m/explain (-graphql-dsl-lang ::Document) x))

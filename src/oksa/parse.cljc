(ns oksa.parse
  (:require [malli.core :as m]
            [oksa.util :as util]
            [oksa.alpha.protocol :refer [Argumented
                                         AST
                                         Serializable
                                         Representable
                                         UpdateableOption]
             :as protocol]
            [oksa.unparse]))

(def -directives-empty-state [])
(def -variables-empty-state [])

(declare -transform-map)

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
   ::TypeName [:and
               [:not [:enum :oksa/list]]
               [:or :keyword :string]
               [:fn {:error/message (str "invalid character range for name, should follow the pattern: " util/re-type-name)}
                (fn [x] (re-matches util/re-type-name (name x)))]]
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
                   [::SelectionSet [:+ [:catn
                                        [::node [:schema [:ref ::Selection]]]
                                        [::children [:? [:schema [:ref ::SelectionSet]]]]]]]]
   ::Selection [:orn
                [::FragmentSpread [:schema [:ref ::FragmentSpread]]]
                [::InlineFragment [:schema [:ref ::InlineFragment]]]
                [::NakedField [:schema [:ref ::NakedField]]]
                [::WrappedField [:schema [:ref ::Field]]]]
   ::Field [:orn [::Field [:cat
                           [:schema [:ref ::FieldName]]
                           [:map
                            [:alias {:optional true} [:ref ::Alias]]
                            [:arguments {:optional true}
                             [:ref ::Arguments]]
                            [:directives {:optional true}
                             [:ref ::Directives]]]
                           [:? [:schema [:ref ::SelectionSet]]]]]]
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
             [:fn {:error/message (str "invalid character range for name, should follow the pattern: " util/re-name)}
              (fn [x] (re-matches util/re-name (name x)))]]
            [:or :keyword :string])
   ::VariableName [:and [:or :keyword :string]
                   [:fn {:error/message (str "invalid character range for variable name, should follow the pattern: " util/re-variable-name)}
                    (fn [x] (re-matches util/re-variable-name (name x)))]]
   ::FragmentName [:and [:or :keyword :string]
                   [:fn {:error/message (str "invalid character range for fragment name, should follow the pattern: " util/re-fragment-name)}
                    (fn [x] (re-matches util/re-fragment-name (name x)))]]
   ::Value [:or
            number?
            :string
            :boolean
            :nil
            [:and :keyword
             [:fn
              {:error/message (str "invalid character range for value, should follow either: " util/re-enum-value ", or: " util/re-variable-reference)}
              (fn [x]
                (let [s (name x)]
                  (or (re-matches util/re-variable-reference s)
                      (re-matches util/re-enum-value s))))]]
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

(def -graphql-dsl-parser (m/parser (-graphql-dsl-lang ::Document))) ; TODO
(defn -field-parser [opts] (m/parser (-graphql-dsl-lang opts ::Field)))
(defn -fragment-spread-parser [opts] (m/parser (-graphql-dsl-lang opts ::FragmentSpread)))
(defn -inline-fragment-parser [opts] (m/parser (-graphql-dsl-lang opts ::InlineFragment)))
(defn -naked-field-parser [opts] (m/parser (-graphql-dsl-lang opts ::NakedField)))
(defn -selection-set-parser [opts] (m/parser (-graphql-dsl-lang opts ::SelectionSet)))
(defn -operation-definition-parser [opts] (m/parser (-graphql-dsl-lang opts ::OperationDefinition)))
(defn -fragment-definition-parser [opts] (m/parser (-graphql-dsl-lang opts ::FragmentDefinition)))
(def -directives-parser (m/parser (-graphql-dsl-lang ::Directives)))
(def -directive-parser (m/parser (-graphql-dsl-lang ::Directive)))
(def -directive-name-parser (m/parser (-graphql-dsl-lang ::DirectiveName)))
(def -arguments-parser (m/parser (-graphql-dsl-lang ::Arguments)))
(def -alias-parser (m/parser (-graphql-dsl-lang ::Alias)))
(defn -name-parser
  ([] (m/parser (-graphql-dsl-lang ::Name)))
  ([opts] (m/parser (-graphql-dsl-lang opts ::Name))))
(def -value-parser (m/parser (-graphql-dsl-lang ::Value)))
(def -type-name-parser (m/parser (-graphql-dsl-lang ::TypeName)))
(def -named-type-or-non-null-named-type-parser (m/parser (-graphql-dsl-lang ::NamedTypeOrNonNullNamedType)))
(def -list-type-or-non-null-list-type-parser (m/parser (-graphql-dsl-lang ::ListTypeOrNonNullListType)))
(def -variable-definitions-parser (m/parser (-graphql-dsl-lang ::VariableDefinitions)))

(defn -parse-or-throw
  [type form parser message]
  (let [retval (parser form)]
    (if (not= retval :malli.core/invalid)
      retval
      (throw (ex-info message
                      (cond
                        (= oksa.util/mode "debug") {:malli.core/explain (malli.core/explain
                                                                          (-graphql-dsl-lang type)
                                                                          form)}
                        (= oksa.util/mode "default") {}
                        :else (throw (ex-info "incorrect `oksa.api/mode` (system property), expected one of `default` or `debug`" {:mode oksa.util/mode}))))))))

(defn -get-oksa-opts
  [opts]
  (into {} (filter #(= (namespace (first %)) "oksa") opts)))

(defn -document
  [definitions]
  (let [form (into [:oksa/document {}] (map protocol/-form definitions))
        [type opts _definitions :as document*] (oksa.parse/-parse-or-throw :oksa.parse/Document
                                                                           form
                                                                           oksa.parse/-graphql-dsl-parser
                                                                           "invalid document")]
    (reify
      AST
      (-type [_] type)
      (-opts [_] opts)
      (-form [_] form)
      Serializable
      (-unparse [_ opts]
        (oksa.unparse/unparse-document opts definitions))
      Representable
      (-gql [this opts] (protocol/-unparse this opts)))))

(defn- parse
  [x]
  (let [parsed (-graphql-dsl-parser x)]
    (if (not= :malli.core/invalid parsed)
      parsed
      (throw (ex-info "invalid form" {})))))

(defn -operation-definition-form
  [operation-type opts selection-set]
  [operation-type (or opts {}) (protocol/-form selection-set)])

(defn -create-operation-definition
  [type opts selection-set]
  (let [form (-operation-definition-form type opts selection-set)]
    (reify
      AST
      (-type [_] type)
      (-opts [_] (-> opts
                     (update :directives (partial oksa.util/transform-malli-ast -transform-map))
                     (update :variables (partial oksa.util/transform-malli-ast -transform-map))))
      (-form [_] form)
      Serializable
      (-unparse [this opts]
        (oksa.unparse/unparse-operation-definition
         (clojure.core/name (protocol/-type this))
         (merge (-get-oksa-opts opts) (protocol/-opts this))
         selection-set))
      Representable
      (-gql [this opts] (protocol/-unparse this opts)))))

(defn -operation-definition
  [operation-type opts selection-set]
  (let [opts (or opts {})
        [_type opts _selection-set] (oksa.parse/-parse-or-throw :oksa.parse/OperationDefinition
                                                                (-operation-definition-form operation-type opts selection-set)
                                                                (oksa.parse/-operation-definition-parser opts)
                                                                "invalid operation definition")]
    (-create-operation-definition operation-type opts selection-set)))

(defn -fragment
  [opts selection-set]
  (let [form [:oksa/fragment opts (protocol/-form selection-set)]
        [type opts _selection-set
         :as fragment*] (oksa.parse/-parse-or-throw :oksa.parse/FragmentDefinition
                                                    form
                                                    (oksa.parse/-fragment-definition-parser opts)
                                                    "invalid fragment definition")]
    (reify
      AST
      (-type [_] type)
      (-opts [_] (update opts :directives (partial oksa.util/transform-malli-ast -transform-map)))
      (-form [_] form)
      Serializable
      (-unparse [this opts]
        (oksa.unparse/unparse-fragment-definition
          (merge (-get-oksa-opts opts) (protocol/-opts this))
          selection-set))
      Representable
      (-gql [this opts] (protocol/-unparse this opts)))))

(defn -selection-set
  [opts selections]
  (let [form (mapv #(if (satisfies? AST %) (protocol/-form %) %) selections)
        [type _selections
         :as parsed-form] (oksa.parse/-parse-or-throw :oksa.parse/SelectionSet
                                                      form
                                                      (oksa.parse/-selection-set-parser opts)
                                                      "invalid selection-set")]
    (reify
      AST
      (-type [_] type)
      (-opts [_] opts)
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

(declare -arguments)

(defn -field
  [name opts selection-set]
  (let [opts (or opts {})
        form (cond-> [name opts]
               (some? selection-set) (conj (protocol/-form selection-set)))
        [type [_field-name field-opts _selection-set*]
         :as field*] (oksa.parse/-parse-or-throw :oksa.parse/Field
                                                 form
                                                 (oksa.parse/-field-parser opts)
                                                 "invalid field")]
    (reify
      AST
      (-type [_] type)
      (-opts [_]
        (cond-> (update field-opts :directives (partial oksa.util/transform-malli-ast -transform-map))
          (:arguments field-opts) (update :arguments -arguments)))
      (-form [_] form)
      Serializable
      (-unparse [this opts] (oksa.unparse/unparse-field name
                                                        (merge (-get-oksa-opts opts)
                                                               (protocol/-opts this))
                                                        selection-set)))))

(defn -naked-field
  [opts name]
  (let [naked-field* (oksa.parse/-parse-or-throw :oksa.parse/NakedField
                                                 name
                                                 (oksa.parse/-naked-field-parser opts)
                                                 "invalid naked field")]
    (reify
      AST
      (-type [_] :oksa.parse/NakedField)
      (-opts [_] {})
      (-form [_] naked-field*)
      Serializable
      (-unparse [_ opts]
        (let [name-fn (:oksa/name-fn opts)]
          (oksa.parse/-parse-or-throw :oksa.parse/Name
                                      (if name-fn
                                        (name-fn naked-field*)
                                        (clojure.core/name naked-field*))
                                      (oksa.parse/-name-parser {:oksa/strict true})
                                      "invalid naked field"))))))

(defn -fragment-spread
  [opts]
  (let [form [:oksa/fragment-spread opts]
        [type opts :as fragment-spread*] (oksa.parse/-parse-or-throw :oksa.parse/FragmentSpread
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
      (-form [_] form)
      Serializable
      (-unparse [this opts]
        (oksa.unparse/unparse-fragment-spread
          (merge
            (-get-oksa-opts opts)
            (protocol/-opts this)))))))

(defn -inline-fragment
  [opts selection-set]
  (let [opts (or opts {})
        form (cond-> [:oksa/inline-fragment opts] (some? selection-set) (conj (protocol/-form selection-set)))
        [type opts :as inline-fragment*] (oksa.parse/-parse-or-throw :oksa.parse/InlineFragment
                                                                     form
                                                                     (oksa.parse/-inline-fragment-parser opts)
                                                                     "invalid inline fragment parser")]
    (reify
      AST
      (-type [_] type)
      (-opts [_] (update opts
                         :directives
                         (partial oksa.util/transform-malli-ast
                                  -transform-map)))
      (-form [_] form)
      Serializable
      (-unparse [this opts]
        (oksa.unparse/unparse-inline-fragment
          (merge (-get-oksa-opts opts)
                 (protocol/-opts this))
          selection-set)))))

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

(defn -directives
  [directives]
  (let [form (mapv protocol/-form directives)
        [type _directives
         :as directives*] (oksa.parse/-parse-or-throw :oksa.parse/Directives
                                                      form
                                                      oksa.parse/-directives-parser
                                                      "invalid directives")]
    (reify
      AST
      (-type [_] type)
      (-form [_] form)
      (-opts [_] {})
      UpdateableOption
      (-update-key [_] :directives)
      (-update-fn [this] #((fnil into -directives-empty-state) % (protocol/-form this)))
      Serializable
      (-unparse [_ _opts]
        (oksa.unparse/format-directives directives)))))

(declare -type)

(defn -list
  [opts type-or-list]
  (let [type-or-list* (if (or (keyword? type-or-list) (string? type-or-list))
                        (-type type-or-list)
                        type-or-list)
        form [:oksa/list opts (protocol/-form type-or-list*)]
        list* (oksa.parse/-parse-or-throw :oksa.parse/ListTypeOrNonNullListType
                                          form
                                          oksa.parse/-list-type-or-non-null-list-type-parser
                                          "invalid list")]
    (reify
      AST
      (-type [_] :oksa.parse/ListTypeOrNonNullListType)
      (-form [_] form)
      (-opts [_] opts)
      Serializable
      (-unparse [this _opts]
        (oksa.unparse/-format-list type-or-list*
                                   (merge (-get-oksa-opts opts)
                                          (protocol/-opts this)))))))

(defn -type
  [type-name]
  (let [form type-name
        type* (oksa.parse/-parse-or-throw :oksa.parse/TypeName
                                          form
                                          oksa.parse/-type-name-parser
                                          "invalid type")]
    (reify
      AST
      (-type [_] :oksa.parse/TypeName)
      (-form [_] form)
      (-opts [_] {})
      Serializable
      (-unparse [this _opts] (clojure.core/name (protocol/-form this))))))

(defn -type!
  [type-name]
  (let [form [type-name {:non-null true}]
        [type-name* _opts :as non-null-type*] (oksa.parse/-parse-or-throw :oksa.parse/NamedTypeOrNonNullNamedType
                                                                          form
                                                                          oksa.parse/-named-type-or-non-null-named-type-parser
                                                                          "invalid non-null type")]
    (reify
      AST
      (-type [_] :oksa.parse/NamedTypeOrNonNullNamedType)
      (-form [_] form)
      (-opts [_] {})
      Serializable
      (-unparse [_ _opts] (str (clojure.core/name type-name*) "!")))))

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
      (oksa.unparse/-format-variable-definition
       variable-name
       (merge
        (-get-oksa-opts opts)
        (protocol/-opts this))
       variable-type))))

(defn -variable-form
  [variable-name opts variable-type]
  (cond-> [variable-name]
    (some? opts) (conj opts)
    true (conj (protocol/-form variable-type))))

(defn -coerce-variable-type
  [variable-type]
  (if (or (keyword? variable-type) (string? variable-type))
    (-type variable-type)
    variable-type))

(defn -variable
  [variable-name opts variable-type]
  (let [variable-type* (-coerce-variable-type variable-type)
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
                            (let [variable-type* (if (or (keyword? variable-type) (string? variable-type))
                                                   (-type variable-type)
                                                   variable-type)]
                              (into acc [variable-name (protocol/-form variable-type*)])))
                          []))
        variables* (oksa.parse/-parse-or-throw :oksa.parse/VariableDefinitions
                                               form
                                               oksa.parse/-variable-definitions-parser
                                               "invalid variable definitions")]
    (reify
      AST
      (-type [_] :oksa.parse/VariableDefinitions)
      (-form [_] form)
      (-opts [_] {})
      UpdateableOption
      (-update-key [_] :variables)
      (-update-fn [this] #((fnil into oksa.parse/-variables-empty-state) % (protocol/-form this))))))

(defn -directive
  [name arguments]
  (let [opts (if (satisfies? protocol/Argumented arguments)
               {:arguments (protocol/-arguments arguments)}
               (cond-> {} (not-empty arguments) (assoc :arguments arguments)))
        form [name opts]
        directive* (oksa.parse/-parse-or-throw :oksa.parse/Directive
                                               form
                                               oksa.parse/-directive-parser
                                               "invalid directive")]
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
        (oksa.unparse/format-directive name (merge
                                             (-get-oksa-opts opts)
                                             (protocol/-opts this)))))))

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
      (-update-fn [this] (constantly (protocol/-form this))))))

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
      (-update-fn [this] (constantly (protocol/-form this))))))

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
      (-update-fn [this] (constantly (protocol/-form this))))))

(defn -argument
  [name value]
  (let [form {name value}
        argument* (oksa.parse/-parse-or-throw :oksa.parse/Arguments
                                              form
                                              oksa.parse/-arguments-parser
                                              "invalid argument")]
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
      (-unparse [_ _opts] (oksa.unparse/-format-argument name value)))))

(defn -arguments
  [arguments]
  (let [form arguments]
    (oksa.parse/-parse-or-throw :oksa.parse/Arguments
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
        (-unparse [_ _opts] (oksa.unparse/-format-arguments arguments*))))))

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
      (-unparse [_ _opts] (str "=" (oksa.unparse/format-value value))))))

(def -transform-map
  (letfn [(operation [operation-type opts [xs]]
            (-create-operation-definition operation-type opts xs))
          (document [_opts xs]
            (-document xs))
          (fragment [{:keys [directives] :as options} xs]
            (assert (some? (:name options)) "missing name")
            (apply -fragment
                   (-opts
                     (-name (:name options))
                     (when (:on options) (-on (:on options)))
                     (when directives (oksa.util/transform-malli-ast -transform-map directives)))
                   xs))
          (fragment-spread [{:keys [directives] :as options}]
            (assert (some? (:name options)) "missing name")
            (-fragment-spread
              (-opts
                (-name (:name options))
                (when directives (oksa.util/transform-malli-ast -transform-map directives)))))
          (inline-fragment [{:keys [directives] :as options} selection-set]
            (assert (not (some? (:name options))) "inline fragments can't have name")
            (apply -inline-fragment
             (-opts
              (when (:on options) (-on (:on options)))
              (when directives (oksa.util/transform-malli-ast -transform-map directives)))
             selection-set))
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
                         (-field name opts xs))
     :oksa.parse/Directives (fn [x]
                              (-directives x))
     :oksa.parse/Directive (fn [[name opts]]
                             (-directive name (:arguments opts)))
     :oksa.parse/DirectiveName (fn [directive-name]
                                 (-directive directive-name nil))
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
                            (-type type-name))
     :oksa.parse/NamedTypeOrNonNullNamedType (fn [[type-name _opts]]
                                               (-type! type-name))
     :oksa.parse/ListTypeOrNonNullListType (fn [[_ {:keys [non-null]} type]]
                                             (if non-null
                                               (-list {:non-null true} type)
                                               (-list {:non-null false} type)))
     :oksa.parse/AbbreviatedListType (fn [[type]]
                                       (-list {:non-null false} type))
     :oksa.parse/AbbreviatedNonNullListType (fn [[_ type-or-list :as x]]
                                              (-list {:non-null true} type-or-list))}))

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

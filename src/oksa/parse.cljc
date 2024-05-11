(ns oksa.parse
  (:require [malli.core :as m]
            [oksa.util :as util]))

(def -transform-map
  (letfn [(operation [operation-type opts xs]
            (into [operation-type (-> opts
                                      (update :directives (partial util/transform-malli-ast -transform-map))
                                      (update :variables (partial util/transform-malli-ast -transform-map)))]
                  xs))
          (document [opts xs]
            (into [:document opts] xs))
          (fragment [opts xs]
            (assert (some? (:name opts)) "missing name")
            (into [:fragment (update opts
                                     :directives
                                     (partial util/transform-malli-ast -transform-map))]
                  xs))
          (fragment-spread [opts & xs]
            (assert (some? (:name opts)) "missing name")
            (into [:fragment-spread
                   (update opts
                           :directives
                           (partial util/transform-malli-ast -transform-map))]
                  xs))
          (inline-fragment [opts xs]
            (assert (not (some? (:name opts))) "inline fragments can't have name")
            (into [:inline-fragment (update opts
                                            :directives
                                            (partial util/transform-malli-ast -transform-map))]
                  xs))
          (selection-set [xs]
           (into [:selectionset {}]
                 (map (fn [{:oksa.parse/keys [node children] :as x}]
                        (let [[selection-type value] node]
                          (cond-> (into [:selection {}]
                                        [(case selection-type
                                           ::NakedField (util/transform-malli-ast -transform-map [::Field [value {}]])
                                           ::WrappedField (util/transform-malli-ast -transform-map value)
                                           ::FragmentSpread (util/transform-malli-ast -transform-map value)
                                           ::InlineFragment (util/transform-malli-ast -transform-map value))])
                            (some? children) (into [(util/transform-malli-ast -transform-map children)]))))
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
               (fragment-spread opts xs)
               (inline-fragment opts xs))))
     :oksa/fragment-spread fragment-spread
     :oksa/inline-fragment inline-fragment
     ::SelectionSet selection-set
     ::Field (fn [[name opts & xs]]
               (into [:selection {} [:field (merge (update opts
                                                           :directives
                                                           (partial util/transform-malli-ast -transform-map))
                                                   {:name name})]]
                     (filterv some? xs)))
     ::Directives (partial into [])
     ::Directive (fn [[name opts]] [name opts])
     ::DirectiveName (fn [directive-name] [directive-name {}])
     ::VariableDefinitions (fn [xs]
                             (map (fn [[variable-name opts type :as _variable-definition]]
                                    [variable-name
                                     (update opts :directives (partial util/transform-malli-ast -transform-map))
                                     type])
                                  xs))
     ::TypeName (fn [type-name] [type-name {}])
     ::NamedTypeOrNonNullNamedType identity
     ::ListTypeOrNonNullListType identity
     ::AbbreviatedListType (fn [x] (into [:oksa/list {}] x))
     ::AbbreviatedNonNullListType (fn [[_ name-or-list]] (into [:oksa/list {:non-null true}] [name-or-list]))}))

(def ^:private reserved-keywords
  (set (into (filter #(some-> % namespace (= "oksa")) (keys -transform-map))
             #{:<> :# :...})))

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
                [:fn #(not (reserved-keywords %))]]
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

(defn- parse
  [x]
  (let [parsed (-graphql-dsl-parser x)]
    (if (not= :malli.core/invalid parsed)
      parsed
      (throw (ex-info "invalid form" {})))))

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

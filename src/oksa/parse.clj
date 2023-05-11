(ns oksa.parse
  (:require [malli.core :as m]
            [oksa.util :as util]))

(def transform-map
  (letfn [(operation [operation-type opts xs]
            (into [operation-type (-> opts
                                      (update :directives (partial util/transform-malli-ast transform-map))
                                      (update :variables (partial util/transform-malli-ast transform-map)))]
                  xs))
          (document [opts xs]
            (into [:document opts] xs))
          (fragment [opts xs]
            (assert (some? (:name opts)) "missing name")
            (into [:fragment opts] xs))
          (fragment-spread [opts & xs]
            (assert (some? (:name opts)) "missing name")
            (into [:fragment-spread opts] xs))
          (inline-fragment [opts xs]
            (assert (not (some? (:name opts))) "inline fragments can't have name")
            (into [:inline-fragment opts] xs))
          (selection-set [xs]
           (into [:selectionset {}]
                 (map (fn [{:oksa.parse/keys [node children] :as x}]
                        (let [[selection-type value] node]
                          (cond-> (into [:selection {}]
                                        [(case selection-type
                                           ::NakedField (util/transform-malli-ast transform-map [::Field [value {}]])
                                           ::WrappedField (util/transform-malli-ast transform-map value)
                                           ::FragmentSpread (util/transform-malli-ast transform-map value)
                                           ::InlineFragment (util/transform-malli-ast transform-map value))])
                            (some? children) (into [(util/transform-malli-ast transform-map children)]))))
                      xs)))]
    {:document document
     :<> document
     :fragment fragment
     :# fragment
     :query (partial operation :query)
     :mutation (partial operation :mutation)
     :subscription (partial operation :subscription)
     :select (fn [opts xs] (into [:selectionset opts] xs))
     :... (fn fragment-dispatcher
            ([opts]
             (fragment-dispatcher opts []))
            ([opts xs]
             (if (some? (:name opts))
               (fragment-spread opts xs)
               (inline-fragment opts xs))))
     :fragment-spread fragment-spread
     :inline-fragment inline-fragment
     ::SelectionSet selection-set
     ::Field (fn [[name opts & xs]]
               (into [:selection {} [:field (merge (update opts
                                                           :directives
                                                           (partial util/transform-malli-ast transform-map))
                                                   {:name name})]]
                     (filterv some? xs)))
     ::Directives (partial into [])
     ::Directive (fn [[name opts]] [name opts])
     ::DirectiveName (fn [directive-name] [directive-name {}])
     ::VariableDefinitions (fn [xs]
                             (map (fn [[variable-name opts type :as _variable-definition]]
                                    [variable-name
                                     (update opts :directives (partial util/transform-malli-ast transform-map))
                                     type])
                                  xs))
     ::TypeName (fn [type-name] [type-name {}])
     ::NamedTypeOrNonNullNamedType identity
     ::ListTypeOrNonNullListType identity
     ::AbbreviatedListType (fn [x] (into [:oksa/list {}] x))
     ::AbbreviatedNonNullListType (fn [[_ name-or-list]] (into [:oksa/list {:oksa/non-null? true}] [name-or-list]))}))

(def graphql-dsl-lang
  [:schema {:registry {::Document [:or
                                   [:schema [:ref ::Definition]]
                                   [:cat
                                    [:enum :document :<>]
                                    [:? :map]
                                    [:+ [:schema [:ref ::Definition]]]]]
                       ::Definition [:schema [:ref ::ExecutableDefinition]]
                       ::ExecutableDefinition [:or
                                               [:schema [:ref ::OperationDefinition]]
                                               [:schema [:ref ::FragmentDefinition]]]
                       ::FragmentDefinition [:cat
                                             [:enum :fragment :#]
                                             [:? [:map [:on [:ref ::Name]]]]
                                             [:+ [:schema [:ref ::SelectionSet]]]]
                       ::OperationDefinition [:or
                                              [:cat
                                               [:enum :query :mutation :subscription]
                                               [:? [:map
                                                    [:name {:optional true} [:ref ::Name]]
                                                    [:variables {:optional true}
                                                     [:schema [:ref ::VariableDefinitions]]]
                                                    [:directives {:optional true}
                                                     [:schema [:ref ::Directives]]]]]
                                               [:+ [:schema [:ref ::SelectionSet]]]]
                                              [:schema [:ref ::SelectionSet]]]
                       ::VariableDefinitions [:orn [::VariableDefinitions
                                                    [:+ [:cat
                                                         [:schema [:ref ::Name]]
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
                       ::TypeName [:and [:not [:enum :oksa/list]] :keyword]
                       ::TypeOpts [:map
                                   [:oksa/non-null? :boolean]]
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
                                    [::WrappedField [:schema [:ref ::Field]]]
                                    [::NakedField [:schema [:ref ::NakedField]]]
                                    [::FragmentSpread [:schema [:ref ::FragmentSpread]]]
                                    [::InlineFragment [:schema [:ref ::InlineFragment]]]]
                       ::Field [:orn [::Field [:cat
                                               [:schema [:ref ::FieldName]]
                                               [:map
                                                [:alias {:optional true} [:ref ::Name]]
                                                [:arguments {:optional true}
                                                 [:ref ::Arguments]]
                                                [:directives {:optional true}
                                                 [:ref ::Directives]]]
                                               [:? [:schema [:ref ::SelectionSet]]]]]]
                       ::NakedField [:schema [:ref ::FieldName]]
                       ::FieldName [:and
                                    [:schema [:ref ::Name]]
                                    [:not (into [:enum] (keys transform-map))]]
                       ::FragmentSpread [:cat
                                         [:enum :fragment-spread :...]
                                         [:? :map]]
                       ::InlineFragment [:cat
                                         [:enum :inline-fragment :...]
                                         [:? :map]
                                         [:+ [:schema [:ref ::SelectionSet]]]]
                       ::Name [:or :keyword :string]
                       ::Value [:or
                                number?
                                :string
                                :boolean
                                :nil
                                :keyword
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
                       ::DirectiveName :keyword}}
   ::Document])

(def graphql-dsl-parser (m/parser graphql-dsl-lang))

(defn parse
  [x]
  (let [parsed (graphql-dsl-parser x)]
    (if (not= :malli.core/invalid parsed)
      parsed
      (throw (ex-info "invalid form" {})))))

(defn xf
  [ast]
  (util/transform-malli-ast transform-map ast))

(defn to-ast
  [x]
  (-> (parse x)
      (xf)))

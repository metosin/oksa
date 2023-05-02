(ns oksa.parse
  (:require [malli.core :as m]
            [oksa.util :as util]))

(def transform-map
  (letfn [(operation [operation-type opts & xs]
            (into [operation-type opts] xs))
          (document [opts xs]
            (into [:document opts] xs))
          (fragment [opts & xs]
            (assert (some? (:name opts)) "missing name")
            (into [:fragment opts] xs))
          (fragment-spread [opts & xs]
            (assert (some? (:name opts)) "missing name")
            (into [:fragment-spread opts] xs))
          (inline-fragment [opts & xs]
            (assert (not (some? (:name opts))) "inline fragments can't have name")
            (into [:inline-fragment opts] xs))]
    (fn [x]
      (or ({:document document
            :<> document
            :fragment fragment
            :# fragment
            :query (partial operation :query)
            :mutation (partial operation :mutation)
            :subscription (partial operation :subscription)
            ::short-form-select (fn [xs] (into [:selectionset {}] xs))
            :select (fn [opts xs] (into [:selectionset opts] xs))
            :... (fn [opts xs]
                   (if (some? (:name opts))
                     (fragment-spread opts xs)
                     (inline-fragment opts xs)))
            :fragment-spread fragment-spread
            :inline-fragment inline-fragment} x)
          ((fn [x]
             (cond
               (or (keyword? x) (string? x))
               (fn [opts & xs]
                 (into [:selection {} [:field (merge opts {:name x})]]
                       (filter some? xs))))) x)))))

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
                                             [:schema [:ref ::SelectionSet]]]
                       ::OperationDefinition [:or
                                              [:cat
                                               [:enum :query :mutation :subscription]
                                               [:? [:map
                                                    [:name {:optional true} [:ref ::Name]]
                                                    [:variable-definitions {:optional true}
                                                     [:schema [:ref ::VariableDefinitions]]]
                                                    [:directives {:optional true}
                                                     [:ref ::Directives]]]]
                                               [:schema [:ref ::SelectionSet]]]
                                              [:schema [:ref ::SelectionSet]]]
                       ::VariableDefinitions [:map-of [:ref ::Name] [:schema [:ref ::Type]]]
                       ::Type [:or
                               [:schema [:ref ::NamedTypeOrNonNullNamedType]]
                               [:schema [:ref ::ListTypeOrNonNullListType]]]
                       ::TypeName [:and [:not [:enum :oksa/list]] :keyword]
                       ::VariableDefinitionOpts [:map
                                                 [:oksa/non-null? :boolean]]
                       ::NamedTypeOrNonNullNamedType [:cat
                                                      [:schema [:ref ::TypeName]]
                                                      [:? [:schema [:ref ::VariableDefinitionOpts]]]]
                       ::ListTypeOrNonNullListType [:cat
                                                    [:= :oksa/list]
                                                    [:? [:schema [:ref ::VariableDefinitionOpts]]]
                                                    [:or
                                                     [:schema [:ref ::NamedTypeOrNonNullNamedType]]
                                                     [:schema [:ref ::ListTypeOrNonNullListType]]]]
                       ::SelectionSet [:or
                                       [:cat
                                        [:= :select]
                                        [:? :map]
                                        [:+ [:schema [:ref ::Selection]]]]
                                       [:altn
                                        [::short-form-select [:+ [:schema [:ref ::Selection]]]]]]
                       ::Selection [:or
                                    [:schema [:ref ::Field]]
                                    [:schema [:ref ::FragmentSpread]]
                                    [:schema [:ref ::InlineFragment]]]
                       ::Field [:cat
                                [:schema [:ref ::Name]]
                                [:? [:map
                                     [:alias {:optional true} [:ref ::Name]]
                                     [:arguments {:optional true}
                                      [:ref ::Arguments]]]]
                                [:? [:schema [:ref ::SelectionSet]]]]
                       ::FragmentSpread [:cat
                                         [:enum :fragment-spread :...]
                                         [:? :map]]
                       ::InlineFragment [:cat
                                         [:enum :inline-fragment :...]
                                         [:? :map]
                                         [:schema [:ref ::SelectionSet]]]
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
                       ::Directives [:+ [:schema [:ref ::Directive]]]
                       ::Directive [:cat
                                    :keyword
                                    [:? [:map
                                         [:arguments {:optional true}
                                          [:ref ::Arguments]]]]]}}
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

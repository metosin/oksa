(ns apollo-example.core
  (:require [helix.core :refer [defnc $]]
            [helix.dom :as d]
            ["react-dom" :as rdom]
            ["apollo-boost" :default ApolloClient :refer [gql]]
            ["@apollo/react-hooks" :as apollo :refer [useQuery]]
            [cljs-bean.core :as b]
            [oksa.core :as oksa]
            [oksa.parse]))

(def client (ApolloClient. #js {:uri "https://countries.trevorblades.com"}))

(defnc Countries [_]
  (let [{:keys [errors data]} (-> [:oksa/query [[:countries {:arguments {:filter {:continent {:eq "EU"}}}}
                                                 [:capital :currency]]]]
                                  (oksa/unparse)
                                  (gql)
                                  (useQuery)
                                  ;; wrap result in a bean to destructure
                                  (b/bean :recursive true))]
    (cond
      errors (d/div "Error :(")
      :else (d/div "Success"
             (for [country (:countries data)]
               (let [{:keys [capital currency]} country]
                 (d/div {:key capital}
                        (d/p capital ": " currency))))))))

(defnc App [_]
  ($ apollo/ApolloProvider
     {:client client}
     (d/div ($ Countries))))

(defn ^:dev/after-load start []
  (rdom/render ($ App) (.getElementById js/document "app")))

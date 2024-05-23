(require '[babashka.classpath :refer [add-classpath]]
         '[clojure.java.shell :refer [sh]]
         '[clojure.string :as str]
         '[babashka.http-client :as http]
         '[cheshire.core :as json]
         '[oksa.core :as oksa])

(println (str "GraphQL conferences in Finland"))
(println "==============================")
(println)
(println
  (->> (-> (http/post "https://api.react-finland.fi/graphql"
             {:headers {:content-type "application/json"}
              :body (json/encode {:query (oksa/unparse [:conferences
                                                        [:name
                                                         :organizers [:company]
                                                         :startDate
                                                         :endDate
                                                         :slogan
                                                         :websiteUrl
                                                         :locations [:country [:name]
                                                                     :city
                                                                     :address]
                                                         :organizers [:company]
                                                         :schedules [:day :intervals [:begin :end] :description]]])
  :variables nil})})
           (update :body #(json/decode % true))
           (get-in [:body :data :conferences]))
       (sort-by :startDate #(compare %2 %1))
       (str/join "\n")))

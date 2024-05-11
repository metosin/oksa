(ns oksa.alpha.protocol)

(defprotocol AST
  (-type [this] "returns the type of the parsed object")
  (-opts [this] "returns the options of the token")
  (-form [this] "returns a malli-parseable representation of the token"))

(defprotocol Serializable
  "Represents an object which can be serialized to represent a partial GraphQL document."
  (-unparse [this opts] "produces a string representation of the GraphQL token"))

(defprotocol Representable
  "Represents an object that can be provided to `oksa.alpha.api/gql`."
  (-gql [this opts] "returns a GraphQL request string"))

(defprotocol UpdateableOption
  "Represents an option that can be provided to `oksa.alpha.api/opts`."
  (-update-key [this] "the associated key to perform the update on")
  (-update-fn [this] "an update function provided for `clojure.core/update`"))

(defprotocol Argumented
  "Represents an option that can be `oksa.alpha.api/directive`."
  (-arguments [this] "returns all of the arguments"))

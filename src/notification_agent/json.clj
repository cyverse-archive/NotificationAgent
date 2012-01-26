(ns notification-agent.json
  (:import [org.codehaus.jackson JsonFactory JsonToken]))

;; The notification query service gets a lot of data back from the OSM, and
;; clojure.data.json was taking nearly two seconds to parse it all.  The
;; purpose of the functions in this namespace is to parse the JSON in a way
;; that is compatible with clojure.data.json, but to do it faster.

(def json-factory ^{:private true} (JsonFactory.))

;; Convenience vars for JsonToken enumeration constants.
(def value-false  ^{:private true} JsonToken/VALUE_FALSE)
(def value-null   ^{:private true} JsonToken/VALUE_NULL)
(def value-float  ^{:private true} JsonToken/VALUE_NUMBER_FLOAT)
(def value-int    ^{:private true} JsonToken/VALUE_NUMBER_INT)
(def value-string ^{:private true} JsonToken/VALUE_STRING)
(def value-true   ^{:private true} JsonToken/VALUE_TRUE)
(def start-object ^{:private true} JsonToken/START_OBJECT)
(def end-object   ^{:private true} JsonToken/END_OBJECT)
(def field-name   ^{:private true} JsonToken/FIELD_NAME)
(def start-array  ^{:private true} JsonToken/START_ARRAY)
(def end-array    ^{:private true} JsonToken/END_ARRAY)

(defn- unexpected-token-type
  "Throws an exception indicating that an unexpected token type was
   encountered."
  [token]
  (throw (IllegalStateException. (str "unexpected token type " + token))))

(declare get-value)

(defn- add-field
  "Adds a field that is obtained from a parser to a JSON object result.  The
   current token referenced by the parser must be the field name."
  [result parser]
  (let [namek (keyword (.getCurrentName parser))
        value (get-value parser (.nextToken parser))]
    (assoc result namek value)))

(defn- parse-object
  "Parses a JSON object.  The current token referenced by the parser must be
   the START_OBJECT token."
  [parser]
  (loop [token (.nextToken parser)
         result {}]
    (condp = token
      field-name    (let [new-result (add-field result parser)]
                      (recur (.nextToken parser) new-result))
      end-object    result
      (unexpected-token-type token))))

(defn- parse-array
  "Parses a JSON array.  The current token referenced by the parser must be
   the START_ARRAY token."
  [parser]
  (loop [token (.nextToken parser)
         result []]
    (if (= token end-array)
      result
      (let [new-result (conj result (get-value parser token))]
        (recur (.nextToken parser) new-result)))))

(defn- get-value
  "Gets a value from the current token in the JSON parser.  The current token
   may correspond to a scalar value or the start of an object or array."
  [parser token]
  (condp = token
    value-false  false
    value-null   nil
    value-float  (.getFloatValue parser)
    value-int    (.getIntValue parser)
    value-string (.getText parser)
    value-true   true
    start-object (parse-object parser)
    start-array  (parse-array parser)
    (unexpected-token-type token)))

(defn read-json
  "Parses a JSON string."
  [json]
  (let [parser (.createJsonParser json-factory json)]
    (get-value parser (.nextToken parser))))

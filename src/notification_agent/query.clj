(ns notification-agent.query
 (:use [notification-agent.common]
       [notification-agent.config]
       [notification-agent.messages :only [sort-messages]]
       [notification-agent.search]
       [clojure.string :only [blank? lower-case]]
       [slingshot.slingshot :only [throw+]])
 (:require [clojure.data.json :as json]
           [clojure.tools.logging :as log]))

(defn- count-messages*
  "Counts the number of matching messages in the OSM."
  [query]
  (let [total (count-matching-messages query)]
    (json-resp 200 (json/json-str {:total (str total)}))))

(defn- get-messages*
  "Retrieves notification messages from the OSM."
  [query]
  (let [results (query-osm query)
        body    (extract-messages query results)]
    (json-resp 200 (json/json-str body))))

(defn- required-string
  "Extracts a required string argument from the query-string map."
  [k m]
  (let [v (m k)]
    (when (blank? v)
      (throw+ {:type  :illegal-argument
               :code  ::missing-or-empty-param
               :param (name k)}))
    v))

(defn- optional-long
  "Extracts an optional long argument from the query-string map, using a default
   value if the argument wasn't provided."
  [k m d]
  (let [v (k m)]
    (if-not (nil? v)
      (string->long v ::invalid-long-integer-param
                    {:param (name k)
                     :value v})
      d)))

(defn- optional-boolean
  "Extracts an optional Boolean argument from the query-string map."
  ([k m]
     (optional-boolean k m nil))
  ([k m d]
     (let [v (k m)]
       (if (nil? v) d (Boolean/valueOf v)))))

(defn- as-keyword
  "Converts a string to a lower-case keyword."
  [s]
  (keyword (lower-case s)))

(defn get-unseen-messages
  "Looks up all messages in the OSM that have not been seen yet for a specified
   user."
  [{:keys [user]}]
  (log/debug "retrieving unseen messages for" user)
  (get-messages* {:user       user
                  :limit      0
                  :offset     0
                  :seen       false
                  :sort-field :timestamp
                  :sort-dir   :asc}))

(defn get-paginated-messages
  "Provides a paginated view for notification messages.  This endpoint takes
   several query-string parameters:

       user      - the name of the user to get notifications for
       limit     - the maximum number of messages to return or zero if there is
                   no limit - optional (0)
       offset    - the number of leading messages to skip - optional (0)
       seen      - specify 'true' for only seen messages or 'false' for only
                   unseen messages - optional (defaults to displaying both seen
                   and unseen messages)
       sortField - the field to use when sorting the messages - optional
                   (currently, only 'timestamp' can be used)
       sortDir   - the sort direction, 'asc' or 'desc' - optional (desc)
       filter    - filter by message type ('data', 'analysis', etc.)"
  [query-params]
  (let [query {:user       (required-string :user query-params)
               :limit      (optional-long :limit query-params 0)
               :offset     (optional-long :offset query-params 0)
               :seen       (optional-boolean :seen query-params)
               :sort-field (as-keyword (:sortfield query-params "timestamp"))
               :sort-dir   (as-keyword (:sortdir query-params "desc"))
               :filter     (:filter query-params)}]
    (get-messages* query)))

(defn count-messages
  "Provides a way to retrieve the number of messages that match a set of
   criteria.  This endpoint takes several query-string parameters:

       user      - the name of the user to count notifications for
       seen      - specify 'true' for only seen messages or 'false' for only
                   unseen messages - optional (defaults to counting both seen
                   and unseen messages)
       filter    - filter by message type ('data', 'analysis', etc.)"
  [query-params]
  (let [query {:user       (required-string :user query-params)
               :seen       (optional-boolean :seen query-params)
               :filter     (:filter query-params)}]
    (count-messages* query)))

(defn last-ten-messages
  "Obtains the ten most recent notifications for the user in ascending order."
  [query-params]
  (let [query   {:user       (required-string :user query-params)
                 :limit      10
                 :offset     0
                 :sort-field :timestamp
                 :sort-dir   :desc}
        results (query-osm query)
        body    (extract-messages query results)
        msgs    (:messages body)
        key-fn  #(get-in % [:message :timestamp])
        body    (assoc body :messages (sort-by key-fn msgs))]
    (json-resp 200 (json/json-str body))))

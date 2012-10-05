(ns notification-agent.query
 (:use [notification-agent.common]
       [notification-agent.config]
       [notification-agent.messages]
       [notification-agent.time]
       [clojure.string :only [blank? lower-case]]
       [slingshot.slingshot :only [throw+ try+]])
 (:require [clojure.data.json :as json]
           [clojure-commons.osm :as osm]
           [clojure.tools.logging :as log]
           [notification-agent.json :as na-json]))

(defn- format-query
  "Formats the query to be sent to the OSM."
  [query]
  (into {} (remove (comp nil? val)
                   {:state.user    (:user query)
                    :state.deleted false
                    :state.seen    (:seen query)
                    :state.type    (:filter query)})))

(defn- query-osm
  "Queries the OSM for the messages that the caller wants to see."
  [query]
  (log/debug "sending a query to the OSM:" query)
  (let [result (osm/query (notifications-osm) (format-query query))
        obj (na-json/read-json result)]
    obj))

(defn- extract-messages
  "Extracts at most limit notification messages from objects returned by the
   OSM.  If limit is nil or nonpositive then all of the notification messages
   will be returned."
  [query results]
  (log/debug "extracting messages from" results)
  (let [limit      (:limit query)
        offset     (:offset query)
        sort-field (:sort-field query)
        sort-dir   (:sort-dir query)
        messages   (:objects results)
        messages   (sort-messages messages sort-field sort-dir)
        msg-count  (count messages)
        messages   (if (> offset 0) (drop offset messages) messages)
        messages   (if (> limit 0) (take limit messages) messages)
        reformat   #(reformat-message (:object_persistence_uuid %) (:state %))
        messages   (map reformat messages)]
    {:total    msg-count
     :messages messages}))

(defn- count-messages*
  "Counts the number of matching messages in the OSM."
  [query]
  (let [results (query-osm query)
        body    (extract-messages query results)]
    (json-resp 200 (json/json-str (select-keys body [:total])))))

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
               :sort-field (as-keyword (:sortField query-params "timestamp"))
               :sort-dir   (as-keyword (:sortDir query-params "desc"))
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
               :limit      1
               :offset     0
               :seen       (optional-boolean :seen query-params)
               :sort-field :timestamp
               :sort-dir   :desc
               :filter     (:filter query-params)}]
    (count-messages* query)))

(ns notification-agent.query
 (:use [notification-agent.common]
       [notification-agent.config]
       [notification-agent.messages]
       [notification-agent.time]
       [clojure.string :only [blank?]]
       [slingshot.slingshot :only [throw+ try+]])
 (:require [clojure.data.json :as json]
           [clojure-commons.osm :as osm]
           [clojure.tools.logging :as log]
           [notification-agent.json :as na-json]))

(defn- format-query
  "Formats the query to be sent to the OSM."
  [query]
  (let [osm-query {:state.user (:user query) :state.deleted false}]
    (if (nil? (:seen query))
      osm-query
      (assoc osm-query :state.seen (:seen query)))))

(defn- query-osm
  "Queries the OSM for the messages that the caller wants to see."
  [query]
  (log/debug "sending a query to the OSM:" query)
  (let [result (osm/query (notifications-osm) (format-query query))
        obj (na-json/read-json result)]
    obj))

(defn- filter-by-type
  "Filters messages by type."
  [type messages]
  (if (blank? type)
    messages
    (filter #(= type (get-in % [:state :type])) messages)))

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
        messages   (filter-by-type (:filter query) messages)
        messages   (sort-messages messages sort-field sort-dir)
        msg-count  (count messages)
        messages   (if (> offset 0) (drop offset messages) messages)
        messages   (if (> limit 0) (take limit messages) messages)
        reformat   #(reformat-message (:object_persistence_uuid %) (:state %))
        messages   (map reformat messages)]
    {:total    msg-count
     :messages messages}))

(defn- get-messages*
  "Retrieves notification messages from the OSM."
  [query]
  (let [results (query-osm query)
        body    (extract-messages query results)]
    (json-resp 200 (json/json-str body))))

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
                  :sort-dir   :des}))

(defn- required-string
  "Extracts a required string argument from the query-string map."
  [k m]
  (let [v (m k)]
    (when (blank? v)
      (throw+ {:type  :illegal-argument
               :code  ::missing-or-empty-param
               :param (name k)}))
    v))

(defn- required-long
  "Extracts a required long argument from the query-string map."
  [k m]
  (let [v (required-string k m)]
    (try+
     (Long/parseLong v)
     (catch NumberFormatException e
       (throw+ {:type  :illegal-argument
                :code  ::invalid-long-integer-param
                :param (name k)
                :value v})))))

(defn get-paginated-messages
  "Provides a paginated view for notification messages.  This endpoint takes
   several query-string parameters:

       user      - the name of the user to get notifications for
       limit     - the maximum number of messages to return
       offset    - the number of leading messages to skip
       sortField - the field to use when sorting the messages - optional
                   (currently, only 'timestamp' can be used)
       sortDir   - the sort direction, 'asc' or 'des' - optional (des)
       filter    - filter by message type ('data', 'analysis', etc.)

   The limit and offset are the only fields that are currently required."
  [query-params]
  (let [query {:user       (required-string :user query-params)
               :limit      (required-long :limit query-params)
               :offset     (required-long :offset query-params)
               :sort-field (keyword (:sortField query-params "timestamp"))
               :sort-dir   (keyword (:sortDir query-params "des"))
               :filter     (:filter query-params)}]
    (get-messages* query)))
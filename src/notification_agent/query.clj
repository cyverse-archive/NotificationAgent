(ns notification-agent.query
 (:use [notification-agent.common]
       [notification-agent.config]
       [notification-agent.messages]
       [notification-agent.time]
       [clojure.pprint :only (pprint)])
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
  (let [result (osm/query (notifications-osm) (format-query query))
        obj (na-json/read-json result)]
    obj))

(defn- update-seen-flag
  "Updates the seen flag in a notification message."
  [{id :object_persistence_uuid state :state}]
  (when (not (:seen state))
    (osm/update-object (notifications-osm) id (assoc state :seen true))))

(defn- get-message-timestamp
  "Extracts the timestamp from a notification message and converts it to an
   instance of java.util.Date."
  [msg]
  (parse-timestamp (:timestamp (:message (:state msg)))))

(defn- sort-messages
  "Sorts messages by message timestamp."
  [messages]
  (sort-by #(get-message-timestamp %) messages))

(defn- extract-messages
  "Extracts at most limit notification messages from objects returned by the
   OSM.  If limit is nil or nonpositive then all of the notification messages
   will be returned."
  [limit results]
  (let [messages (sort-messages (:objects results))]
    (map #(do (update-seen-flag %)
            (reformat-message (:object_persistence_uuid %) (:state %)))
      (if (and (number? limit) (> limit 0))
        (take-last limit messages)
        messages))))

(defn- get-messages*
  "Retrieves notification messages from the OSM."
  [query]
  (let [body {:messages (extract-messages (:limit query) (query-osm query))}]
    (json-resp 200 (json/json-str body))))

(defn get-messages
  "Looks up messages in the OSM that may or may not have been seen yet.  The
   sender can specify that the query return either seen or unseen messages by
   specifying the 'seen' flag value in the request JSON.  The request is in
   the following format:

   {
       \"user\":  \"username\",
       \"seen\":  false,
       \"limit\": 50
   }

   Where both the 'seen' and 'limit' fields are optional.  If the 'seen' field
   is omitted then both seen and unseen messages will be returned.  If the
   'limit' field is omitted then all messages that satisfy the other criteria
   will be returned."
  [body]
  (get-messages* (parse-body body)))

(defn get-unseen-messages
  "Looks up messages in the OSM that have not been seen yet.  All other search
   criteria being the same, this function is equivalent to calling get-messages
   and specifying a 'seen' flag of 'false'."
  [body]
  (get-messages* (assoc (parse-body body) :seen false)))

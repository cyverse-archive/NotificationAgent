(ns notification-agent.query
 (:use [clj-time.core]
       [notification-agent.config]
       [notification-agent.time]
       [clojure.pprint :only (pprint)])
 (:require [clojure.data.json :as json]
           [clojure-commons.json :as cc-json]
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
  (let [result (osm/query notifications-osm (format-query query))
        obj (time (na-json/read-json result))]
    obj))

(defn- update-seen-flag
  "Updates the seen flag in a notification message."
  [{id :object_persistence_uuid state :state}]
  (osm/update-object notifications-osm id (assoc state :seen true)))

(defn- reformat-message
  "Converts a message from the format stored in the OSM to the format that the
   DE expects."
  [{:keys [state]}]
  (-> state
    (assoc-in [:message :timestamp] (timestamp->millis (get-in state [:message :timestamp])))
    (assoc-in [:payload :startdate] (timestamp->millis (get-in state [:payload :startdate])))
    (assoc-in [:payload :enddate] (timestamp->millis (get-in state [:payload :enddate])))))

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
    (map #(do (reformat-message %))
      (if (and (number? limit) (> limit 0))
        (take-last limit messages)
        messages))))

(defn- get-messages*
  "Retrieves notification messages from the OSM."
  [query]
  (json/json-str
    {:messages (extract-messages (:limit query) (query-osm query))}))

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
  (get-messages* (cc-json/body->json body)))

(defn get-unseen-messages
  "Looks up messages in the OSM that have not been seen yet.  All other search
   criteria being the same, this function is equivalent to calling get-messages
   and specifying a 'seen' flag of 'false'."
  [body]
  (get-messages* (assoc (cc-json/body->json body) :seen false)))

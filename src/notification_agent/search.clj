(ns notification-agent.search
  (:use [notification-agent.config]
        [notification-agent.messages])
  (:require [notification-agent.json :as na-json]
            [clojure.tools.logging :as log]
            [clojure-commons.osm :as osm]))

(defn- format-query
  "Formats the query to be sent to the OSM."
  [query]
  (into {} (remove (comp nil? val)
                   {:state.user    (:user query)
                    :state.deleted false
                    :state.seen    (:seen query)
                    :state.type    (:filter query)})))

(defn query-osm
  "Queries the OSM for the messages that the caller wants to see."
  [query]
  (log/debug "sending a query to the OSM:" query)
  (let [result (osm/query (notifications-osm) (format-query query))
        obj (na-json/read-json result)]
    obj))

(defn extract-messages
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
        msg-count  (count messages)
        messages   (sort-messages messages sort-field sort-dir)
        messages   (if (> offset 0) (drop offset messages) messages)
        messages   (if (> limit 0) (take limit messages) messages)
        reformat   #(reformat-message (:object_persistence_uuid %) (:state %))
        messages   (map reformat messages)]
    {:total    (str msg-count)
     :messages messages}))

(defn count-matching-messages
  "Sends a request to the OSM to count messages matching a query."
  [query]
  (log/debug "sending a request to count messages to the OSM:" query)
  (let [result (osm/count-documents (notifications-osm) (format-query query))
        obj    (na-json/read-json result)]
    (:count obj)))


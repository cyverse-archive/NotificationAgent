(ns notification-agent.delete
 (:use [notification-agent.common]
       [notification-agent.config])
 (:require [clojure.tools.logging :as log]
           [clojure-commons.osm :as osm]
           [notification-agent.json :as na-json]))

(defn- log-missing-message
  "Logs a message indicating that an attempt was made to delete a non-existent
   message."
  [uuid]
  (log/warn "attempt to delete non-existent message" uuid "ignored"))

(defn- get-msg
  "Retrieves the message identified by uuid from the OSM."
  [uuid]
  (log/trace "retrieving message" uuid "from the OSM")
  (let [body (osm/query (notifications-osm) {:object_persistence_uuid uuid})]
    (first (:objects (na-json/read-json body)))))

(defn- delete-message
  "Marks a single message as deleted."
  [uuid]
  (log/debug "marking message" uuid "as deleted")
  (let [msg (get-msg uuid)]
    (if (nil? msg)
      (log-missing-message uuid)
      (osm/update-object (notifications-osm) uuid
        (assoc (:state msg) :deleted true)))))

(defn- delete-messages*
  "Deletes messages corresponding to a list of message identifiers."
  [uuids]
  (dorun (map #(delete-message %) uuids)))

(defn delete-messages
  "Handles a message deletion request.  The request body should consist of
   a JSON array of message UUIDs."
  [body]
  (log/debug "handling a notification message deletion request")
  (let [request (parse-body body)]
    (if (and (map? request) (vector? (:uuids request)))
      (do
        (delete-messages* (:uuids request))
        (resp 200 nil))
      (resp 400 "The request body must contain a list of identifiers.\n"))))

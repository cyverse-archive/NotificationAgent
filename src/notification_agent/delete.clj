(ns notification-agent.delete
 (:use [notification-agent.common]
       [notification-agent.config]
       [notification-agent.search :only [count-matching-messages query-osm]]
       [slingshot.slingshot :only [throw+]])
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

(defn- mark-message-deleted
  "Marks a single message as deleted."
  [{uuid :object_persistence_uuid state :state}]
  (log/debug "marking message" uuid "as deleted")
  (osm/update-object
    (notifications-osm)
    uuid
    (assoc state :deleted true)))

(defn- delete-message
  "Marks a single message as deleted."
  [uuid]
  (let [msg (get-msg uuid)]
    (if (nil? msg)
      (log-missing-message uuid)
      (mark-message-deleted msg))))

(defn- delete-messages*
  "Deletes messages corresponding to a list of message identifiers."
  [uuids]
  (dorun (map #(delete-message %) uuids)))

(defn- delete-all-messages*
  "Deletes all messages found that match the given osm query request."
  [request]
  (let [msgs (query-osm request)]
    (dorun
      (->> msgs
        (:objects)
        (remove nil?)
        (map #(mark-message-deleted %))))))

(defn delete-messages
  "Handles a message deletion request.  The request body should consist of
   a JSON array of message UUIDs."
  [body]
  (log/debug "handling a notification message deletion request")
  (let [request (parse-body body)]
    (if (and (map? request) (vector? (:uuids request)))
      (do
        (delete-messages* (:uuids request))
        (success-resp))
      (throw+ {:type  :illegal-argument
               :code  ::no-identifiers-in-request
               :param "uuids"
               :value body}))))

(defn delete-all-messages
  "Handles a messages deletion request.  The request body should consist of
   a JSON object containing the user name and any other osm query filters to
   delete matching messages."
  [body]
  (log/debug "handling a notification delete all request")
  (let [request (parse-body body)
        user (validate-user (:user request))]
    (log/debug "deleting notifications for" user)
    (delete-all-messages* request)
    (success-resp {:count (str (count-matching-messages
                                 {:user user
                                  :seen false}))})))

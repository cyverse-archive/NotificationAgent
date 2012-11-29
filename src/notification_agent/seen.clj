(ns notification-agent.seen
  (:use [notification-agent.common]
        [notification-agent.config]
        [notification-agent.search :only [count-matching-messages]]
        [slingshot.slingshot :only [throw+ try+]])
  (:require [clojure.data.json :as json]
            [clojure-commons.osm :as osm]
            [clojure.tools.logging :as log]
            [notification-agent.json :as na-json]))

(defn- update-seen-flag
  "Updates the seen flag in a notification message."
  [{id :object_persistence_uuid state :state}]
  (log/trace "updating the seen flag for message:" id)
  (when (not (:seen state))
    (osm/update-object (notifications-osm) id (assoc state :seen true))))

(defn- get-notification
  "Gets a notification from the OSM."
  [uuid]
  (try+
   (na-json/read-json (osm/get-object (notifications-osm) uuid))
   (catch [:body "URL does not exist."] _
     (log/warn (str "attempt to mark non-existent message, " uuid
                    ", as seen ignored"))
     nil)
   (catch Object e
     (log/error e "unexpected exception")
     (throw+))))

(defn- validate-uuids
  "Validates the list of UUIDs that was passed in."
  [uuids body]
  (when (empty? uuids)
    (throw+ {:type  :illegal-argument
             :code  ::no-identifiers-in-request
             :param "uuids"
             :value body})))

(defn mark-messages-seen
  "Marks one or more notification messages as seen."
  [body {:keys [user]}]
  (validate-user user)
  (let [uuids (:uuids (na-json/read-json body))]
    (validate-uuids uuids body)
    (dorun
     (->> uuids
          (map get-notification)
          (remove nil?)
          (map update-seen-flag)))
    (success-resp {:count (str (count-matching-messages
                                {:user user
                                 :seen false}))})))

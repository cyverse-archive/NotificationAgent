(ns notification-agent.seen
  (:use [notification-agent.common]
        [notification-agent.config]
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

(defn mark-messages-seen
  "Marks one or more notification messages as seen."
  [body]
  (let [uuids (:uuids (na-json/read-json body))]
    (when (nil? uuids)
      (throw+ {:type  :illegal-argument
               :code  ::no-identifiers-in-request
               :param "uuids"
               :value body}))
    (dorun (map update-seen-flag
                (filter (comp not nil?)
                        (map get-notification uuids))))
    (success-resp)))

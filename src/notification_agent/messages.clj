(ns notification-agent.messages
 (:use [notification-agent.time])
 (:require [clojure.tools.logging :as log]))

(defn- fix-timestamp
  "Some timestamps are stored in the default timestamp format used by
   JavaScript.  The DE needs all timestamps to be represented as milliseconds
   since the epoch.  This function fixes timestamps that are in the wrong
   format."
  [timestamp]
  (let [ts (str timestamp)]
    (if (re-matches #"^\d*$" ts) ts (timestamp->millis ts))))

(defn reformat-message
  "Converts a message from the format stored in the OSM to the format that the
   DE expects."
  [uuid state]
  (-> state
    (assoc-in [:message :id] uuid)
    (update-in [:message :timestamp] fix-timestamp)
    (update-in [:payload :startdate] fix-timestamp)
    (update-in [:payload :enddate] fix-timestamp)))

(defn- get-message-timestamp
  "Extracts the timestamp from a notification message and converts it to an
   instance of java.util.Date."
  [msg]
  (let [timestamp (parse-timestamp (get-in msg [:state :message :timestamp]))]
    (log/warn "timestamp:" timestamp)))

(defn sort-messages
  "Sorts messages in ascending order by message timestamp."
  [messages]
  (sort-by #(get-message-timestamp %) messages))

(ns notification-agent.messages
 (:use [notification-agent.time]))

(defn- replace-value
  "Replaces a value defined by ks in a nested map, m, with the result of
   calling f on the original value."
  [m f ks]
  (assoc-in m ks (f (get-in m ks))))

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
    (replace-value fix-timestamp [:message :timestamp])
    (replace-value fix-timestamp [:payload :startdate])
    (replace-value fix-timestamp [:payload :enddate])))

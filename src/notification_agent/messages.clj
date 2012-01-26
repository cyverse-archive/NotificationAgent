(ns notification-agent.messages
 (:use [notification-agent.time]))

(defn- replace-value
  "Replaces a value defined by ks in a nested map, m, with the result of
   calling f on the original value."
  [m f ks]
  (assoc-in m ks (f (get-in m ks))))

(defn reformat-message
  "Converts a message from the format stored in the OSM to the format that the
   DE expects."
  [uuid state]
  (-> state
    (assoc-in [:message :id] uuid)
    (replace-value timestamp->millis [:message :timestamp])
    (replace-value timestamp->millis [:payload :startdate])
    (replace-value timestamp->millis [:payload :enddate])))

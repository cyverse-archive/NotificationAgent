(ns notification-agent.messages
 (:use [notification-agent.config]
       [notification-agent.messages]
       [notification-agent.time])
 (:require [clj-http.client :as client]
           [clojure-commons.osm :as osm]
           [clojure.data.json :as json]
           [clojure.tools.logging :as log])
 (:import [java.io IOException]))

(defn- fix-timestamp
  "Some timestamps are stored in the default timestamp format used by
   JavaScript.  The DE needs all timestamps to be represented as milliseconds
   since the epoch.  This function fixes timestamps that are in the wrong
   format."
  [timestamp]
  (let [ts (str timestamp)]
    (if (re-matches #"^\d*$" ts) ts (timestamp->millis ts))))

(defn- opt-update-in
  "Updates a value in a map if that value exists."
  [m ks f & args]
  (let [value (get-in m ks)]
    (if (nil? value) m (apply update-in m ks f args))))

(defn reformat-message
  "Converts a message from the format stored in the OSM to the format that the
   DE expects."
  [uuid state]
  (-> state
    (assoc-in [:message :id] uuid)
    (opt-update-in [:message :timestamp] fix-timestamp)
    (opt-update-in [:payload :startdate] fix-timestamp)
    (opt-update-in [:payload :enddate] fix-timestamp)
    (dissoc :email_request)))

(defn- send-email-request
  "Sends an e-mail request to the iPlant e-mail service."
  [request]
  (log/debug "sending an e-mail request:" request)
  (client/post (email-url)
               {:body (json/json-str request)
                :content-type :json}))

(defn- persist-msg
  "Persists a message in the OSM."
  [msg]
  (log/debug "saving a message in the OSM:" msg)
  (osm/save-object (notifications-osm) msg))

(defn- send-msg-to-recipient
  "Forawards a message to a single recipient."
  [url msg]
  (log/debug "sending message to" url)
  (try
    (client/post url {:body msg})
    (catch IOException e
      (log/error e "unable to send message to" url))))

(defn- send-msg
  "Forwards a message to zero or more recipients."
  [msg]
  (let [recipients (notification-recipients)]
    (log/debug "forwarding message to" (count recipients) "recipients")
    (doall (map #(send-msg-to-recipient % msg) recipients))))

(defn persist-and-send-msg
  "Persists a message in the OSM and sends it to any receivers and returns
   the state object."
  [msg]
  (let [uuid (persist-msg msg)
        email-request (:email_request msg)]
    (log/debug "UUID of persisted message:" uuid)
    (when (not (nil? email-request)) (send-email-request email-request))
    (send-msg (json/json-str (reformat-message uuid msg)))))

(defn- get-message-timestamp
  "Extracts the timestamp from a notification message and converts it to an
   instance of java.util.Date."
  [msg]
  (parse-timestamp (get-in msg [:state :message :timestamp])))

(defn sort-messages
  "Sorts messages in ascending order by message timestamp."
  [messages]
  (sort-by #(get-message-timestamp %) messages))

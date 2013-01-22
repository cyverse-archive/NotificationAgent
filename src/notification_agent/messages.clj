(ns notification-agent.messages
  (:use [notification-agent.config]
        [notification-agent.messages]
        [notification-agent.time]
        [slingshot.slingshot :only [throw+]])
  (:require [cheshire.core :as cheshire]
            [clj-http.client :as client]
            [clojure-commons.osm :as osm]
            [clojure.tools.logging :as log]
            [notification-agent.db :as db])
  (:import [java.io IOException]
           [java.util Comparator]))

(defn- fix-timestamp
  "Some timestamps are stored in the default timestamp format used by
   JavaScript.  The DE needs all timestamps to be represented as milliseconds
   since the epoch.  This function fixes timestamps that are in the wrong
   format."
  [timestamp]
  (let [ts (str timestamp)]
    (if (re-matches #"^\d*$" ts) ts (str (timestamp->millis ts)))))

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
  [notification-uuid {:keys [template to] :as request}]
  (log/debug "sending an e-mail request:" request)
  (let [json-request (cheshire/encode request)]
    (client/post (email-url)
                 {:body         json-request
                  :content-type :json})
    (db/record-email-request notification-uuid template to json-request)))

(defn- persist-msg
  "Persists a message in the OSM."
  [{type :type username :user {subject :text created-date :timestamp} :message :as msg}]
  (log/debug "saving a message in the OSM:" msg)
  (db/insert-notification
   (or type "analysis") username subject created-date (cheshire/encode msg)))

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
    (dorun (map #(send-msg-to-recipient % msg) recipients))))

(defn persist-and-send-msg
  "Persists a message in the OSM and sends it to any receivers and returns
   the state object."
  [msg]
  (let [uuid          (persist-msg msg)
        email-request (:email_request msg)]
    (log/debug "UUID of persisted message:" uuid)
    (when-not (nil? email-request)
      (send-email-request uuid email-request))
    (send-msg (cheshire/encode (reformat-message uuid msg)))))

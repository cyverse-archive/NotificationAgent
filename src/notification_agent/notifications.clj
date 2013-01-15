(ns notification-agent.notifications
  (:use [notification-agent.common]
        [notification-agent.config]
        [notification-agent.messages]
        [notification-agent.time])
  (:require [clojure.data.json :as json]
            [clojure.string :as string]
            [clojure.tools.logging :as log]))

(defn- validate-field
  "Verifies that a required field is present in a possibly nested map
   structure."
  [m ks]
  (if (string/blank? (get-in m ks))
    (let [desc (string/join " -> " ks)]
      (throw
        (IllegalArgumentException. (str "missing required field " desc))))))

(def
  ^{:private true
    :doc "The fields that have to be present in the request body."}
  required-fields
  [[:type] [:user] [:subject]])

(defn- validate-request
  "Verifies that an incoming message request contains all of the required
   fields."
  [msg]
  (dorun (map #(validate-field msg %) required-fields)))

(defn- request-to-msg
  "Converts a notification request to a full-fledged message that will be
   stored in the OSM."
  [request]
  {:type (:type request)
   :user (:user request)
   :subject (:subject request)
   :email (:email request false)
   :email_template (:email_template request)
   :payload (:payload request {})
   :message {:id ""
             :timestamp (str (System/currentTimeMillis))
             :text (:message request (:subject request))}})

(defn- email-request
  "Formats an e-mail request for a generic notification."
  [addr template subject payload]
  {:to addr
   :template template
   :subject subject
   :values payload})

(defn- send-email?
  "Determines whether or not an e-mail should be sent to the user."
  [email-requested template addr]
  (and (email-enabled) email-requested (not (string/blank? template))
       (valid-email-addr addr)))

(defn- add-email-request
  "Adds an e-mail request to the notification message if an e-mail is
   requested."
  [msg]
  (let [payload (:payload msg)
        addr (:email_address payload)
        template (:email_template msg)
        subject (:subject msg)]
    (if (send-email? (:email msg) template addr)
      (assoc msg :email_request (email-request addr template subject payload))
      msg)))

(defn handle-notification-request
  "Handles a general notification request."
  [body]
  (let [request (parse-body body)]
    (validate-request request)
    (persist-and-send-msg (add-email-request (request-to-msg request)))
    (success-resp)))

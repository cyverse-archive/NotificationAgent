(ns notification-agent.seen
  (:use [notification-agent.common]
        [slingshot.slingshot :only [throw+]])
  (:require [clojure-commons.error-codes :as ce]
            [notification-agent.db :as db]))

(defn- validate-uuids
  "Validates the list of UUIDs that was passed in."
  [uuids body]
  (when (empty? uuids)
    (throw+ {:error_code ce/ERR_BAD_OR_MISSING_FIELD
             :field_name :uuids
             :body       body})))

(defn- successful-seen-response
  "Returns the response for a successful request to mark messages seen."
  [user]
  (success-resp {:count (str (db/count-matching-messages user {:seen false}))}))

(defn mark-messages-seen
  "Marks one or more notification messages as seen."
  [body {:keys [user]}]
  (validate-user user)
  (let [uuids (:uuids (parse-body body))]
    (validate-uuids uuids body)
    (db/mark-notifications-seen user uuids)
    (successful-seen-response user)))

(defn mark-all-messages-seen
  "Marks all notification messages as seen."
  [body]
  (let [user (validate-user (:user (parse-body body)))]
    (db/mark-matching-notifications-seen user {:seen false})
    (successful-seen-response user)))

(defn mark-system-messages-seen
  "Marks one or more system notifications as seen."
  [body {:keys [user]}]
  (validate-user user)
  (let [uuids (:uuids (parse-body body))]
    (validate-uuids uuids body)
    (db/mark-system-notifications-seen user uuids)
    (success-resp {:count (str (db/count-active-system-notifications user))})))

(defn mark-all-system-messages-seen
  "Marks all system notifications as seen for a user."
  [body]
  (let [user (validate-user (:user (parse-body body)))]
    (db/mark-all-system-notifications-seen user)
    (success-resp {:count (str (db/count-active-system-notifications user))})))

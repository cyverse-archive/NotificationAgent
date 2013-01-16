(ns notification-agent.delete
  (:use [notification-agent.common]
        [slingshot.slingshot :only [throw+]])
  (:require [clojure.tools.logging :as log]
            [clojure-commons.error-codes :as ce]
            [notification-agent.db :as db]))

(defn delete-messages
  "Handles a message deletion request.  The request body should consist of
   a JSON array of message UUIDs."
  [params body]
  (log/debug "handling a notification message deletion request")
  (let [user    (validate-user (:user params))
        request (parse-body body)]
    (if (and (map? request) (vector? (:uuids request)))
      (do
        (db/delete-notifications user (:uuids request))
        (success-resp))
      (throw+ {:error_code   ce/ERR_BAD_OR_MISSING_FIELD,
               :field_name   :uuids
               :request_body body}))))

(defn delete-all-messages
  "Handles a request to delete all messages for a specific user that match"
  [params]
  (log/debug "handling a notification delete-all request")
  (let [user (validate-user (:user params))]
    (log/debug "deleting notifications for" user)
    (db/delete-matching-notifications user params)
    (success-resp {:count (str (db/count-matching-messages user {:seen false}))})))

(ns notification-agent.db
  (:use [korma.db]
        [korma.core]
        [kameleon.notification-entities]
        [notification-agent.config]
        [slingshot.slingshot :only [throw+ try+]])
  (:require [clojure-commons.error-codes :as ce]
            [clojure.string :as string]
            [clojure.tools.logging :as log])
  (:import [java.util Date UUID]))

(defn- create-db-spec
  "Creates the database connection spec to use when accessing the database."
  []
  {:classname   (db-driver-class)
   :subprotocol (db-subprotocol)
   :subname     (str "//" (db-host) ":" (db-port) "/" (db-name))
   :user        (db-user)
   :password    (db-password)})

(defn define-database
  "Defines the database connection to use from within Clojure."
  []
  (let [spec (create-db-spec)]
    (defonce notifications-db (create-db spec))
    (default-connection notifications-db)))

(defn- parse-uuid
  "Parses a UUID in the standard format."
  [uuid]
  (try+
   (UUID/fromString uuid)
   (catch IllegalArgumentException _
     (throw+ {:error_code  ce/ERR_BAD_OR_MISSING_FIELD
              :description "invalid UUID"
              :value       uuid}))))

(defn delete-notifications
  "Marks notifications with selected UUIDs as deleted if they belong to the
   specified user."
  [user uuids]
  (update notifications
          (set-fields {:deleted true})
          (where {:username user
                  :uuids    [in (map parse-uuid uuids)]})))

(defn- parse-date
  "Parses a date that is specified as a string representing the number of
   milliseconds since January 1, 1970."
  [millis param-name]
  (try+
   (Date. (Long/parseLong millis))
   (catch NumberFormatException _
     (throw+ {:error_code  ce/ERR_BAD_QUERY_PARAMETER
              :param_name  param-name
              :param_value millis}))))

(defn- parse-boolean
  "Parses a boolean field that is specified as a string."
  [value]
  (cond (nil? value)              value
        (instance? Boolean value) value
        :else                     (Boolean/parseBoolean value)))

(defn- add-created-before-condition
  "Adds a condition specifying that only notifications older than a specified
   date should be returned."
  [query {:keys [created-before]}]
  (if created-before
    (assoc query :date_created [< (parse-date created-before :created-before)])
    query))

(defn- build-where-clause
  "Builds an SQL where clause for a set of query-string parameters."
  [user {:keys [type subject seen] :as params}]
  (add-created-before-condition
   (into {} (remove (comp nil? val) {:type     type
                                     :username user
                                     :subject  subject
                                     :seen     (parse-boolean seen)}))
   params))

(defn delete-matching-notifications
  "Deletes notifications matching a set of incoming parameters."
  [user params]
  (update notifications
          (set-fields {:deleted true})
          (where (build-where-clause user params))))

(defn count-matching-messages
  "Counts the number of messages matching a set of query-string parameters."
  [user params]
  (:count
   (select notifications
           (aggregate (count :*) :count)
           (where (build-where-clause user params)))))

(defn- parse-old-job-uuid
  "Parses a UUID in the most recent old job UUID format, which is the standard
   UUID format prefixed by a lower-case j."
  [uuid]
  (when (re-matches #"j[-0-9a-fA-F]{36}")
    (parse-uuid (apply str (drop 1 uuid)))))

(defn- chunk-string
  "Splits a string into chunks of specified lengths"
  [s ls]
  (loop [acc      []
         s        s
         [l & ls] ls]
    (let [acc (conj acc (apply str (take l s)))]
      (if ls
        (recur acc (drop l s) ls)
        acc))))

(defn- parse-older-job-uuid
  "Parses a UUID in the original job UUID format, which is in the format of a
   hexadecimal string with no dashes preceded by a lower-case j."
  [uuid]
  (when (re-matches #"j[0-9a-fA-F]{32}")
    (parse-uuid (string/join "-" (chunk-string (rest uuid) [8 4 4 4 12])))))

(defn- parse-job-uuid
  "Parses a UUID associated with a job, which may be in one of several formats."
  [job-uuid]
  (or (parse-old-job-uuid job-uuid)
      (parse-older-job-uuid job-uuid)
      (parse-uuid job-uuid)))

(defn get-notification-status
  "Gets the status of the most recent notification associated with a job."
  [job-uuid]
  ((comp :status first)
   (select analysis_execution_statuses
           (fields :status)
           (where {:uuid (parse-job-uuid job-uuid)}))))

(ns notification-agent.db
  (:use [korma.db]
        [korma.core]
        [kameleon.notification-entities]
        [notification-agent.config]
        [slingshot.slingshot :only [throw+ try+]])
  (:require [clojure-commons.error-codes :as ce]
            [clojure.string :as string]
            [korma.sql.engine :as eng])
  (:import [java.sql Timestamp]
           [java.util UUID]))

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
  (and uuid
       (try+
        (UUID/fromString uuid)
        (catch IllegalArgumentException _
          (throw+ {:error_code  ce/ERR_BAD_OR_MISSING_FIELD
                   :description "invalid UUID"
                   :value       uuid})))))

(defn- parse-date
  "Parses a date that is specified as a string representing the number of
   milliseconds since January 1, 1970."
  [millis & [param-name]]
  (try+
   (Timestamp. (Long/parseLong millis))
   (catch NumberFormatException _
     (if param-name
       (throw+ {:error_code  ce/ERR_BAD_QUERY_PARAMETER
                :param_name  param-name
                :param_value millis})
       (throw+ {:error_code  ce/ERR_BAD_OR_MISSING_FIELD
                :field_name  :timestamp
                :field_value millis})))))

(defn- parse-boolean
  "Parses a boolean field that is specified as a string."
  [value]
  (cond (nil? value)              value
        (instance? Boolean value) (.booleanValue value)
        :else                     (Boolean/parseBoolean value)))

(defn- add-created-before-condition
  "Adds a condition specifying that only notifications older than a specified
   date should be returned."
  [query {:keys [created-before]}]
  (if created-before
    (assoc query :date_created [< (parse-date created-before :created-before)])
    query))

(defn- build-where-clause
  "Builds an SQL where clause for a notification query from a set of query-string parameters."
  [user {:keys [type subject seen] :as params}]
  (add-created-before-condition
   (into {} (remove (comp nil? val) {:type     (or type (:filter params))
                                     :username user
                                     :subject  subject
                                     :seen     (parse-boolean seen)
                                     :deleted  false}))
   params))

(defn- validate-notification-sort-field
  "Validates the sort field for a notification query."
  [sort-field]
  (let [sort-field (if (string? sort-field)
                     (keyword (string/lower-case sort-field))
                     (keyword sort-field))
        sort-field (if (= :timestamp sort-field)
                     :date_created
                     sort-field)]
    (when-not ((set (:fields notifications)) (eng/prefix notifications sort-field))
      (throw+ {:error_code ce/ERR_ILLEGAL_ARGUMENT
               :arg_name   :sort-field
               :arg_value  sort-field}))
    sort-field))

(defn- validate-sort-order
  "Validates the sort order for a query."
  [sort-dir]
  (when sort-dir
    (let [sort-dir (cond (string? sort-dir)  (keyword (string/upper-case sort-dir))
                         (keyword? sort-dir) (keyword (string/upper-case (name sort-dir)))
                         :else               nil)]
      (when-not (#{:ASC :DESC} sort-dir)
        (throw+ {:error_code ce/ERR_ILLEGAL_ARGUMENT
                 :arg_name   :sort-dir
                 :arg_value  sort-dir}))
      sort-dir)))

(defn- add-order-by-clause
  "Adds an order by clause to a notification query if a specific order is requested."
  [query {:keys [sort-field sort-dir]}]
  (if sort-field
    (order
     query
     (validate-notification-sort-field sort-field)
     (validate-sort-order sort-dir))
    query))

(defn- validate-non-negative-int
  "Validates a non-negative integer argument."
  [arg-name arg-value]
  (if (number? arg-value)
    (int arg-value)
    (try+
     (Integer/parseInt arg-value)
     (catch NumberFormatException e
       (throw+ {:error_code ce/ERR_ILLEGAL_ARGUMENT
                :arg_name   arg-name
                :arg_value  arg-value
                :details    (.getMessage e)})))))

(defn- add-limit-clause
  "Adds a limit clause to a notifiation query if a limit is specified."
  [query {v :limit}]
  (if (and v (pos? v))
    (limit query (validate-non-negative-int :limit v))
    query))

(defn- add-offset-clause
  "Adds an offset clause to a notification query if an offset is specified."
  [query {v :offset}]
  (if (and v (pos? v))
    (offset query (validate-non-negative-int :order v))
    query))

(defn delete-notifications
  "Marks notifications with selected UUIDs as deleted if they belong to the
   specified user."
  [user uuids]
  (update notifications
          (set-fields {:deleted true})
          (where {:username user
                  :uuid     [in (map parse-uuid uuids)]})))

(defn delete-matching-notifications
  "Deletes notifications matching a set of incoming parameters."
  [user params]
  (update notifications
          (set-fields {:deleted true})
          (where (build-where-clause user params))))

(defn mark-notifications-seen
  "Marks notifications with selected UUIDs as seen if they belong to the
   specified user."
  [user uuids]
  (update notifications
          (set-fields {:seen true})
          (where {:username user
                  :uuid     [in (map parse-uuid uuids)]})))

(defn mark-matching-notifications-seen
  "Marks notifications matching a set of incoming parameters as seen."
  [user params]
  (update notifications
          (set-fields {:seen true})
          (where (build-where-clause user params))))

(defn count-matching-messages
  "Counts the number of messages matching a set of query-string parameters."
  [user params]
  ((comp :count first)
   (select notifications
           (aggregate (count :*) :count)
           (where (build-where-clause user params)))))

(defn find-matching-messages
  "Finds messages matching a set of query-string parameters."
  [user params]
  (-> (select* notifications)
      (fields :uuid :type :username :subject :seen :deleted :date_created :message)
      (where (build-where-clause user params))
      (add-order-by-clause params)
      (add-limit-clause params)
      (add-offset-clause params)
      (select)))

(defn- parse-old-job-uuid
  "Parses a UUID in the most recent old job UUID format, which is the standard
   UUID format prefixed by a lower-case j."
  [uuid]
  (when (and uuid (re-matches #"j[-0-9a-fA-F]{36}" uuid))
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
  (when (and uuid (re-matches #"j[0-9a-fA-F]{32}" uuid))
    (parse-uuid (string/join "-" (chunk-string (rest uuid) [8 4 4 4 12])))))

(defn- parse-job-uuid
  "Parses a UUID associated with a job, which may be in one of several formats."
  [job-uuid]
  (or (parse-old-job-uuid job-uuid)
      (parse-older-job-uuid job-uuid)
      (parse-uuid job-uuid)
      (throw+ {:error_code  ce/ERR_BAD_OR_MISSING_FIELD
               :description "missing UUID"})))

(defn get-notification-status
  "Gets the status of the most recent notification associated with a job."
  [job-uuid]
  ((comp :status first)
   (select analysis_execution_statuses
           (fields :status)
           (where {:uuid (parse-job-uuid job-uuid)}))))

(defn- now-timestamp
  "Returns an SQL timestamp representing the current date and time."
  []
  (Timestamp. (System/currentTimeMillis)))

(defn update-notification-status
  "Updates the status of the most recent notification associated with a job."
  [job-uuid status]
  (let [uuid (parse-job-uuid job-uuid)]
    (or (update analysis_execution_statuses
                (set-fields {:status        status
                             :date_modified (now-timestamp)})
                (where {:uuid uuid}))
        (insert analysis_execution_statuses
                (values {:status status
                         :uuid   uuid})))))

(defn insert-notification
  "Inserts a notification into the database."
  [type username subject created-date message]
  (let [uuid (UUID/randomUUID)]
    (insert notifications
            (values {:uuid         uuid
                     :type         type
                     :username     username
                     :subject      subject
                     :message      message
                     :date_created (parse-date created-date)}))
    (string/upper-case (str uuid))))

(defn get-notification-id
  [uuid]
  (select notifications
          (fields :id)
          (where {:uuid (parse-uuid uuid)})))

(defn- notification-id-subselect
  "Creates a subselect statement to obtain the primary key for the notification
   with the given UUID."
  [uuid]
  (subselect notifications
             (fields :id)
             (where {:uuid (parse-uuid uuid)})))

(defn record-email-request
  "Inserts a record of an e-mail request into the database."
  [uuid template addr payload]
  (insert email_notification_messages
          (values {:notification_id (notification-id-subselect uuid)
                   :template        template
                   :address         addr
                   :payload         payload})))

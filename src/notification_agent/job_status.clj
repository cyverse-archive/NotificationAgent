(ns notification-agent.job-status
  (:use [clojure.string :only (lower-case)]
        [clojure.pprint :only (pprint)]
        [notification-agent.config]
        [notification-agent.common]
        [notification-agent.messages]
        [notification-agent.time])
  (:require [clojure.data.json :as json]
            [clj-http.client :as client]
            [clojure-commons.osm :as osm]
            [clojure.tools.logging :as log]
            [notification-agent.json :as na-json])
  (:import [java.io IOException]))

(defn- get-descriptive-job-name
  "Extracts a descriptive job name from the job state object.  We can count on
   a useful job description for data notifications, so the job description will
   be used for them.  For other types of notifications the name of the job is
   the best candidate."
  [state]
  (if (= (:type state) "data")
    (:description state)
    (:name state)))

(defn- job-status-msg
  "Formats the status message for a job whose status has changed."
  [state]
  (str (get-descriptive-job-name state) " " (lower-case (:status state))))

(defn- job-completed
  "Determines if a job has completed."
  [state]
  (re-matches #"(?i)\s*(?:completed|failed)\s*" (:status state)))

(defn- format-email-request
  "Formats an e-mail request that can be sent to the iPlant e-mail service."
  [email state]
  {:to email
   :template (email-template)
   :subject (str (:name state) " status changed.")
   :from-addr (email-from-address)
   :from-name (email-from-name)
   :values {:analysisname (:name state)
            :analysisstatus (:status state)
            :analysisstartdate (unparse-epoch-string (:submission_date state))
            :analysisresultsfolder (:output_dir state)
            :analysisdescription (:description state)}})

(defn- email-requested
  "Determines if e-mail notifications were requested for a job.  The 'notify'
   element in the job state indicates whether or not e-mail notifications were
   requested, which is the case if the 'notify' element is both present and
   true."
  [state]
  (:notify state false))

(defn- add-email-request
  "Includes an e-mail request in a notificaiton message if e-mail
   notifications were requested."
  [msg state]
  (let [addr (:email state)]
    (if (and (email-enabled) (email-requested state) (valid-email-addr addr))
      (assoc msg :email_request (format-email-request addr state))
      msg)))

(defn- state-to-msg
  "Converts an object representing a job state to a notification message."
  [state]
  {:type (:type state)
   :user (:user state)
   :deleted false
   :seen false
   :outputDir (:output_dir state)
   :outputManifest (:output_manifest state)
   :message {:id ""
             :timestamp (current-time)
             :text (job-status-msg state)}
   :payload {:id (:uuid state)
             :action "job_status_change"
             :status (:status state)
             :resultfolderid (:output_dir state)
             :user (:user state)
             :name (:name state "")
             :startdate (:submission_date state "")
             :enddate (:completion_date state "")
             :analysis_id (:analysis_id state "")
             :analysis_name (:analysis_name state "")
             :description (:description state "")}})

(defn- handle-completed-job
  "Handles a job status update request for a job that has completed and
   returns the state object."
  [uuid state]
  (log/debug "job" (:name state) "just completed")
  (persist-and-send-msg (add-email-request (state-to-msg state) state)))

(defn- get-notification-status-object
  "Loads the notification status object for the job with the given UUID."
  [uuid]
  (let [query {"state.id" uuid}
        results (na-json/read-json (osm/query (job-status-osm) query))]
    (first (:objects results))))

(defn- get-notification-status
  "Gets the status of the most recent notification associated with a job."
  [uuid]
  (let [obj (get-notification-status-object uuid)]
    (if (nil? obj) "" (get-in obj [:state :status]))))

(defn- update-notification-status
  "Stores the last status seen by the notification agent for a job in the job
   statuses bucket in the OSM."
  [{uuid :uuid status :status job-name :name}]
  (let [status-obj (get-notification-status-object uuid)
        new-state {:id uuid :status status}]
    (log/info "updating the notification status for job" job-name)
    (if (nil? status-obj)
      (osm/save-object (job-status-osm) new-state)
      (osm/update-object
        (job-status-osm) (:object_persistence_uuid status-obj) new-state))))

(defn- handle-status-change
  "Handles a job with a status that has been changed since the job was last
   seen by the notification agent.  To do this, a notification needs to be
   generated and the prevous_status field has to be updated with the last
   status that was seen by the notification agent."
  [uuid state]
  (log/debug "the status of job" (:name state) "changed")
  (let [completed (job-completed state)]
    (if completed
      (handle-completed-job uuid state)
      (persist-and-send-msg (state-to-msg state)))
    (update-notification-status state)))

(defn- job-status-changed
  "Determines whether or not the status of a job corresponding to a state
   object has changed since the last time the notification agent saw the job."
  [state]
  (let [curr-status (:status state)
        uuid (:uuid state)
        prev-status (get-notification-status uuid)]
    (not= curr-status prev-status)))

(defn- get-jobs-with-inconsistent-state
  "Gets a list of jobs whose current status doesn't match the status last seen
   by the notification agent (which is stored in the misnamed previous_status
   field)."
  []
  (log/debug "retrieving the list of jobs that have been updated while the "
             "notification agent was down")
  (let [jobs (:objects (na-json/read-json (osm/query (jobs-osm) {})))]
    (filter #(job-status-changed (:state %)) jobs)))

(defn- fix-job-status
  "Fixes the status of a job with an inconsistent state.  This function is
   basically just a wrapper around handle-status-change that adds some
   exception handling."
  [uuid state]
  (when (not (nil? (:name state)))
    (log/debug "fixing state for job" (:name state))
    (try (handle-status-change uuid state)
      (catch Throwable t
        (log/warn t "unable to fix status for job" (:name state))))))

(defn- fix-inconsistent-state
  "Processes the status changes for any jobs whose state changed without the
   notification agent knowing about it.  This may happen if the system is
   misconfigured or if the notification agent goes down for a while."
  []
  (dorun (map #(fix-job-status (:object_persistence_uuid %) (:state %))
              (get-jobs-with-inconsistent-state))))

(defn initialize-job-status-service
  "Performs any tasks required to initialize the job status service."
  []
  (try
    (fix-inconsistent-state)
    (catch Exception e
      (log/error e "unable to initialize job status service"))))

(defn handle-job-status
  "Handles a job status update request with the given body."
  [body]
  (let [obj (parse-body body)
        state (:state obj)
        uuid (:object_persistence_uuid obj)]
    (log/info "received a job status update request for job" (:name state)
              "with status" (:status state))
    (if (job-status-changed state)
      (handle-status-change uuid state)
      (log/debug "the status of job" (:name state) "did not change"))
    (success-resp)))

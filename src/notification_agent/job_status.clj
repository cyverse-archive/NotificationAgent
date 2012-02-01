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
  (:import [java.net URI]
           [java.io IOException]))

(defn- extract-path
  "Extracts the path component from a URI."
  [addr]
  (.getPath (URI. addr)))

(defn- job-status-msg
  "Formats the status message for a job whose status has changed."
  [state]
  (str "job " (:name state) " " (lower-case (:status state))))

(defn- job-completed
  "Determines if a job has completed."
  [state]
  (re-matches #"(?i)\s*(?:completed|failed)\s*" (:status state)))

(defn- job-just-completed
  "Determines if a job has just completed."
  [state]
  (and (nil? (:completion_date state)) (job-completed state)))

(defn- add-completion-date
  "Adds the completion date to a job state object."
  [state]
  (log/trace "adding a completion date to job " (:name state))
  (assoc state :completion_date (current-time)))

(defn- send-email-request
  "Sends an e-mail request to the iPlant e-mail service."
  [request]
  (log/debug "sending an e-mail request: " request)
  (client/post (email-url)
               {:body (json/json-str request)
                :content-type :json}))

(defn- format-email-request
  "Formats an e-mail request that can be sent to the iPlant e-mail service."
  [email state]
  {:to email
   :template (email-template)
   :subject (str (:name state) " status changed.")
   :values {:analysisname (:name state)
            :analysisstatus (:status state)
            :analysisstartdate (:submission_date state)
            :analysisresultsfolder (extract-path (:output_dir state))
            :analysisdescription (:description state)}})

(defn- email-requested
  "Determines if e-mail notifications were requested for a job.  The 'notify'
   element in the job state indicates whether or not e-mail notifications were
   requested, which is the case if the 'notify' element is both present and
   true."
  [state]
  (:notify state false))

(defn- valid-email-addr
  "Validates an e-mail address."
  [addr]
  (re-matches #"^[^@ ]+@[^@ ]+$" addr))

(defn- send-email-if-requested
  "Sends an e-mail notifying the user of the job status change if e-mail
   notifications were requested."
  [state]
  (let [addr (:email state)]
    (if (and (email-enabled) (email-requested state) (valid-email-addr addr))
      (send-email-request (format-email-request addr state)))))

(defn- state-to-msg
  "Converts an object representing a job state to a notification message."
  [state]
  {:type "analysis"
   :user (:user state)
   :deleted false
   :seen false
   :workspaceId (:workspace_id state)
   :outputDir (:output_dir state)
   :outputManifest (:output_manifest state)
   :message {:id ""
             :timestamp (current-time)
             :text (job-status-msg state)}
   :payload {:id (:uuid state)
             :action "job_status_change"
             :status (:status state)
             :resultfolderid (extract-path (:output_dir state))
             :user (:user state)
             :name (:name state "")
             :startdate (:submission_date state "")
             :enddate (:completion_date state "")
             :analysis_id (:analysis_id state "")
             :analysis_name (:analysis_name state "")
             :description (:description state "")}})

(defn- persist-msg
  "Persists a message in the OSM."
  [msg]
  (log/debug "saving a message in the OSM: " msg)
  (osm/save-object (notifications-osm) msg))

(defn- send-msg-to-recipient
  "Forawards a message to a single recipient."
  [url msg]
  (log/debug "sending message to " url)
  (try
    (client/post url {:body msg})
    (catch IOException e
      (log/error (str "unable to send message to " url ": " e)))))

(defn- send-msg
  "Forwards a message to zero or more recipients."
  [msg]
  (let [recipients (notification-recipients)]
    (log/debug "forwarding message to " (count recipients) " recipients")
    (doall (map #(send-msg-to-recipient % msg) recipients))))

(defn- persist-and-send-msg
  "Persists a message in the OSM and sends it to any receivers and returns
   the state object."
  [uuid state]
  (let [msg (state-to-msg state)
        uuid (persist-msg msg)]
    (log/debug "UUID of persisted message: " uuid)
    (send-msg (json/json-str (reformat-message uuid msg)))))

(defn- load-state
  "Loads the current state of the object from the OSM."
  [uuid]
  (:state (na-json/read-json (osm/get-object (jobs-osm) uuid))))

(defn- persist-completion-date
  "Stores the job completion date in the OSM.  This is done by loading the
   current state from the OSM, updating the completion date in the loaded
   state object and storing the updated object in the OSM.  This reduces,
   albeit does not eliminate the possibility that the state will have been
   updated between the time we got the state and the time we updated it.
   Eventually, the storage of the completion date is going to be moved to
   Panopticon.  This function will be removed at that time."
  [uuid state]
  (log/info "persisting the completion date for job" (:uuid state)
    "with status" (:status state))
  (let [completion-date (:completion_date state)
        new-state (load-state uuid)]
    (osm/update-object (jobs-osm) uuid
      (assoc new-state :completion_date completion-date))))

(defn- handle-just-completed-job
  "Handles a job status update request for a job that has just completed
   and returns the state object."
  [uuid state]
  (log/debug "job " (:name state) " just completed")
  (send-email-if-requested state)
  (persist-and-send-msg uuid state)
  (persist-completion-date uuid state))

(defn- handle-status-change
  "Handles a job with a status that has been changed since the job was last
   seen by the notification agent.  To do this, a notification needs to be
   generated and the prevous_status field has to be updated with the last
   status that was seen by the notification agent."
  [uuid state]
  (log/debug "the status of job " (:name state) " changed")
  (let [just-completed (job-just-completed state)
        new-state (if just-completed (add-completion-date state) state)]
    (if just-completed
      (handle-just-completed-job uuid new-state)
      (persist-and-send-msg uuid new-state))))

(defn- get-jobs-with-inconsistent-state
  "Gets a list of jobs whose current status doesn't match the status last seen
   by the notification agent (which is stored in the misnamed previous_status
   field)."
  []
  (log/debug "retrieving the list of jobs that have been updated while the "
    "notification agent was down")
  (let [query {"$where" "this.state.status != this.state.previous_status"}]
    (:objects (na-json/read-json (osm/query (jobs-osm) query)))))

(defn- fix-job-status
  "Fixes the status of a job with an inconsistent state.  This function is
   basically just a wrapper around handle-status-change that adds some
   exception handling."
  [uuid state]
  (log/debug "fixing state for job " (:name state))
  (try (handle-status-change uuid state)
    (catch Throwable t
      (log/warn t "unable to fix status for job " (:name state)))))

(defn- fix-inconsistent-state
  "Processes the status changes for any jobs whose state changed without the
   notification agent knowing about it.  This may happen if the system is
   misconfigured or if the notification agent goes down for a while."
  []
  (dorun (map #(fix-job-status (:object_persistence_uuid %) (:state %))
           (get-jobs-with-inconsistent-state))))

(defn- get-notification-status
  "Gets the status of the most recent notification associated with a job."
  [uuid]
  (let [results (osm/query (job-status-osm) {"state.uuid" uuid})
        objects (:objects (na-json/read-json results))]
    (if (empty? objects) "" (get-in (first objects) [:state :status]))))

(defn- job-status-changed
  "Determines whether or not the status of a job corresponding to a state
   object has changed since the last time the notification agent saw the job."
  [state]
  (dosync
    (let [curr-status (:status state)
          uuid (:uuid state)
          prev-status (get-notification-status uuid)]
      (= curr-status prev-status))))

(defn- job-status-bucket-empty
  "Determines whether or not the job status bucket is empty."
  []
  (let [results (osm/query (job-status-osm) {})]
    (empty? (:objects (na-json/read-json results)))))

(defn- get-all-notifications
  "Retrieves all notifications from the OSM."
  []
  (:objects (na-json/read-json (osm/query (notifications-osm) {}))))

(defn get-values
  "Gets multiple values from a map."
  [m k & more]
  (map #(get m %) (cons k more)))

(defn- load-all-notifications
  "Loads all of the notifications that have ever been logged, filtering out
   any that appear to be invalid."
  []
  (filter #(not (nil? (get-in % [:state :message :timestamp])))
          (:objects (na-json/read-json (osm/query (notifications-osm) {})))))

(defn load-job-statuses
  "Loads the most recent status seen by the notification agent for all jobs."
  []
  (apply merge
         (map #(hash-map (get-values (get-in % [:state :payload]) :id :status))
              (notification-agent.messages/sort-messages
                (load-all-notifications)))))

(defn- initialize-job-status-bucket
  "Initializes the job status bucket in the OSM."
  []
  (log/info "initializing the job status bucket in the OSM")
  ())

(defn initialize-job-status-service
  "Performs any tasks required to initialize the job status service."
  []
  (when (job-status-bucket-empty) (initialize-job-status-bucket))
  (fix-inconsistent-state))

(defn handle-job-status
  "Handles a job status update request with the given body."
  [body]
  (let [obj (parse-body body)
        state (:state obj)
        uuid (:object_persistence_uuid obj)]
    (log/info "received a job status update request for job" (:uuid state)
      "with status" (:status state))
    (if (job-status-changed state)
      (handle-status-change uuid state)
      (log/debug "the status of job " (:name state) " did not change"))
    (resp 200 nil)))

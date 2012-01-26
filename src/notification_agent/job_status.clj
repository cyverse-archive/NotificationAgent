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
            [clojure.tools.logging :as log])
  (:import [java.net URI]
           [java.io IOException]))

(defn- extract-path
  "Extracts the path component from a URI."
  [addr]
  (.getPath (URI. addr)))

(defn- job-status-changed
  "Determines whether or not the status of a job corresponding to a state
   object has changed."
  [state]
  (not= (:status state) (:previous_status state "")))

(defn- job-status-msg
  "Formats the status message for a job whose status has changed."
  [state]
  (str "job " (:name state) " " (lower-case (:status state))))

(defn- job-just-completed
  "Determines if a job has just completed."
  [state]
  (and (nil? (:completion_date state))
       (re-matches #"(?i)\s*(?:completed|failed)\s*" (:status state))))

(defn- add-completion-date
  "Adds the completion date to a job state object."
  [state]
  (merge state {:completion_date (current-time)}))

(defn- send-email-request
  "Sends an e-mail request to the iPlant e-mail service."
  [request]
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

(defn- send-email-if-requested
  "Sends an e-mail notifying the user of the job status change if e-mail
   notifications were requested."
  [state]
  (let [addr (:email state)]
    (if (and (email-enabled) (email-requested state) (not (nil? addr)))
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
  (osm/save-object (notifications-osm) msg))

(defn- send-msg-to-recipient
  "Forawards a message to a single recipient."
  [url msg]
  (try
    (client/post url {:body msg})
    (catch IOException e
      (log/error (str "unable to send message to " url ": " e)))))

(defn- send-msg
  "Forwards a message to zero or more recipients."
  [msg]
  (doall (map #(send-msg-to-recipient % msg) (notification-recipients))))

(defn- persist-and-send-msg
  "Persists a message in the OSM and sends it to any receivers and returns
   the state object."
  [uuid state]
  (let [msg (state-to-msg state)
        uuid (persist-msg msg)]
    (send-msg (json/json-str (reformat-message uuid msg))))
  state)

(defn- handle-just-completed-job
  "Handles a job status update request for a job that has just completed
   and returns the state object."
  [uuid state]
  (send-email-if-requested state)
  (persist-and-send-msg uuid state))

(defn- handle-updated-job-status
  "Handles a job status update request for a job whose status has actually
   changed.  Job status updates for which the job status did not change are
   ignored.  The state object, which may have been updated, is returned."
  [uuid state]
  (if (job-just-completed state)
    (handle-just-completed-job uuid (add-completion-date state))
    (persist-and-send-msg uuid state)))

(defn update-job-state
  "Updates the job state in the OSM.  This is done so that the completion
   date can be added and the previous status can be updated."
  [uuid state]
  (osm/update-object (jobs-osm) uuid
    (assoc state :previous_status (:status state))))

(defn handle-job-status
  "Handles a job status update request with the given body."
  [body]
  (let [obj (parse-body body)
        state (:state obj)
        uuid (:object_persistence_uuid obj)]
    (if (job-status-changed state)
      (update-job-state uuid (handle-updated-job-status uuid state)))
    (resp 200 nil)))

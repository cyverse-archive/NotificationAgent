(ns notification-agent.job-status
  (:use [clojure.string :only (lower-case)]
        [clj-time.core :only (default-time-zone now)]
        [clj-time.format :only (formatter unparse with-zone)]
        [notification-agent.config]
        [notification-agent.common])
  (:require [clojure.data.json :as json]
	    [clojure-commons.json :as cc-json]
            [clj-http.client :as client]
            [clojure-commons.osm :as osm])
  (:import java.net.URI))

(defn- extract-path
  "Extracts the path component from a URI.

   Parameters:
     addr - the address to extract the path from."
  [addr]
  (.getPath (URI. addr)))

(defn- job-status-changed
  "Determines whether or not the job status has changed.

   Parameters:
     state - the object representing the state of the job."
  [state]
  (not= (:status state) (:previous_status state "")))

(defn- job-status-msg
  "Formats the status message for a job whose status has changed.

   Parameters:
     state - the object representing the state of the job."
  [state]
  (str "job " (:name state) " " (lower-case (:status state))))

(defn- job-just-completed
  "Determines if a job has just completed.

   Parameters:
     state - the object representing the job state."
  [state]
  (and (nil? (:completion_date state))
       (re-matches #"(?i)\s*(?:completed|failed)\s*" (:status state))))

(def date-formatter
  ^{:private true
    :doc "The date formatter that is used to format all timestamps."}
  (with-zone (formatter "EEE, MMM dd YYYY, HH:mm:ss z") (default-time-zone)))

(defn- current-time
  "Returns the current time, formatted in a similar manner to the default
   date and time format used by JavaScript."
  []
  (unparse date-formatter (now)))

(defn- add-completion-date
  "Adds the completion date to the job state object.

   Parameters:
     state - the object representing the state of the job."
  [state]
  (merge state {:completion_date (current-time)}))

(defn- send-email-request
  "Sends an e-mail request to the iPlant e-mail service.

   Parameters:
     request - the e-mail request."
  [request]
  (client/post email-url
               {:body (json/json-str request)
                :content-type :json}))

(defn- format-email-request
  "Formats an e-mail request that can be sent to the iPlant e-mail service.

   Parameters:
     email - the user's e-mail address.
     state - the object representing the state of the job."
  [email state]
  {:to email
   :template email-template
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
   true.

   Parameters:
     state - the object representing the state of the job."
  [state]
  (:notify state false))

(defn- send-email-if-requested
  "Sends an e-mail notifying the user of the job status change if e-mail
   notifications were requested.

   Parameters:
     state - the object representing the state of the job."
  [state]
  (let [addr (:email state)]
    (if (and email-enabled (email-requested state) (not (nil? addr)))
      (send-email-request (format-email-request addr state)))))

(defn- state-to-msg
  "Converts an object representing a job state to a notification message.

   Parameters:
     state - the object representing the state of the job."
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
  "Persists a message in the OSM.

   Parameters:
     msg - the message to persist."
  [msg]
  (osm/save-object notifications-osm msg))

(defn- send-msg
  "Forwards a message to zero or more recipients.

   Parameters:
     msg - the message to send."
  [msg]
  (doall #(client/post % {:body msg}) notification-recipients))

(defn- persist-and-send-msg
  "Persists a message in the OSM and sends it to any receivers.

   Parameters:
     uuid  - the UUID of the OSM object that represents the job.
     state - the object representing the state of the job."
  [uuid state]
  (let [msg (state-to-msg state)]
    (persist-msg msg)
    (send-msg msg)))

(defn- handle-just-completed-job
  "Handles a job status update request for a job that has just completed.

   Parameters:
     uuid  - the UUID of the OSM object that represents the job
     state - the object representing the state of the job."
  [uuid state]
  (send-email-if-requested state)
  (persist-and-send-msg uuid state))

(defn- handle-updated-job-status
  "Handles a job status update request for a job whose status has actually
   changed.  Job status updates for which the job status did not change are
   ignored.

   Parameters:
     uuid  - the UUID of the OSM object that represents the job
     state - the object representing the state of the job"
  [uuid state]
  (if (job-just-completed state)
    (handle-just-completed-job uuid (add-completion-date state))
    (persist-and-send-msg uuid state)))

(defn handle-job-status
  "Handles a job status update request with the given body.

  Parameters:
    body - an input stream that can be used to obtain the request body."
  [body]
  (let [obj (cc-json/body->json body)
        state (:state obj)
        uuid (:object_persistence_uuid obj)]
    (if (job-status-changed state)
      (handle-updated-job-status uuid state))
    (resp 200 nil)))

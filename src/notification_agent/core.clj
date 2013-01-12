(ns notification-agent.core
  (:gen-class)
  (:use [clojure.java.io :only [file]]
        [clojure-commons.lcase-params :only (wrap-lcase-params)]
        [clojure-commons.query-params :only (wrap-query-params)]
        [compojure.core]
        [ring.middleware keyword-params nested-params]
        [notification-agent.common]
        [notification-agent.delete]
        [notification-agent.job-status]
        [notification-agent.notifications]
        [notification-agent.query]
        [notification-agent.seen]
        [slingshot.slingshot :only [try+]])
  (:require [compojure.route :as route]
            [compojure.handler :as handler]
            [clojure.tools.logging :as log]
            [clojure-commons.clavin-client :as cl]
            [notification-agent.config :as config]
            [notification-agent.db :as db]
            [ring.adapter.jetty :as jetty]))

(defn- trap
  [f]
  (try+
   (f)
   (catch [:type :illegal-argument] {:keys [type code param value]}
     (illegal-argument-resp type code param value))
   (catch IllegalArgumentException e
     (error-resp e))
   (catch IllegalStateException e
     (error-resp e))
   (catch Throwable t
     (failure-resp t))))

(defn- job-status
  "Handles a job status update request."
  [body]
  (trap #(handle-job-status body)))

(defn- notification
  "Handles a generic notification request."
  [body]
  (trap #(handle-notification-request body)))

(defn- delete
  "Handles a message deletion request."
  [params body]
  (trap #(delete-messages params body)))

(defn- delete-all
  "Handles a request to delete all messages for a user."
  [params]
  (trap #(delete-all-messages params)))

(defn- unseen-messages
  "Handles a query for unseen messages."
  [query]
  (trap #(get-unseen-messages query)))

(defn- messages
  "Handles a request for a paginated message view."
  [query]
  (trap #(get-paginated-messages query)))

(defn- count-msgs
  "Handles a request to count messages."
  [query]
  (trap #(count-messages query)))

(defn- last-ten
  "Handles a request to get the most recent ten messages."
  [query]
  (trap #(last-ten-messages query)))

(defn- mark-seen
  "Handles a request to mark one or messages as seen."
  [body params]
  (trap #(mark-messages-seen body params)))

(defn- mark-all-seen
  "Handles a request to mark all messages for a user as seen."
  [body]
  (trap #(mark-all-messages-seen body)))

(defroutes notificationagent-routes
  (GET  "/" [] "Welcome to the notification agent!\n")
  (POST "/job-status" [:as {body :body}] (job-status body))
  (POST "/notification" [:as {body :body}] (notification body))
  (POST "/delete" [:as {:keys [params body]}] (delete params body))
  (DELETE "/delete-all" [:as {params :params}] (delete-all params))
  (POST "/seen" [:as {body :body params :params}] (mark-seen body params))
  (POST "/mark-all-seen" [:as {body :body}] (mark-all-seen body))
  (GET  "/unseen-messages" [:as {params :params}] (unseen-messages params))
  (GET  "/messages" [:as {params :params}] (messages params))
  (GET  "/count-messages" [:as {params :params}] (count-msgs params))
  (GET  "/last-ten-messages" [:as {params :params}] (last-ten params))
  (route/not-found "Unrecognized service path.\n"))

(defn site-handler [routes]
  (-> routes
      wrap-keyword-params
      wrap-nested-params
      wrap-query-params))

(def app
  (site-handler notificationagent-routes))

(defn- init-service
  []
  (db/define-database))

(defn- load-config-from-file
  []
  (config/load-config-from-file)
  (init-service))

(defn- load-config-from-zookeeper
  []
  (config/load-config-from-zookeeper)
  (init-service))

(defn -main
  [& args]
  (load-config-from-zookeeper)
  (initialize-job-status-service)
  (log/warn "Listening on" (config/listen-port))
  (jetty/run-jetty (site-handler notificationagent-routes) {:port (config/listen-port)}))

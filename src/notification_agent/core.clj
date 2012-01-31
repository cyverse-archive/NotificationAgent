(ns notification-agent.core
  (:gen-class)
  (:use [compojure.core]
        [ring.middleware keyword-params nested-params]
        [notification-agent.common]
        [notification-agent.config]
        [notification-agent.delete]
        [notification-agent.job-status]
        [notification-agent.query])
  (:require [compojure.route :as route]
            [compojure.handler :as handler]
            [clojure.tools.logging :as log]
            [clojure-commons.props :as cc-props]
            [clojure-commons.clavin-client :as cl]
            [ring.adapter.jetty :as jetty]))

(defn- trap
  [f]
  (try
    (f)
    (catch IllegalArgumentException e (error-resp e))
    (catch IllegalStateException e (error-resp e))
    (catch Throwable t (failure-resp t))))

(defn- job-status
  "Handles a job status update request."
  [body]
  (trap #(handle-job-status body)))

(defn- messages
  "Handles a query for seen or unseen messages."
  [body]
  (trap #(get-messages body)))

(defn- unseen-messages
  "Handles a query for unseen messages."
  [body]
  (trap #(get-unseen-messages body)))

(defn- delete
  "Handles a message deletion request."
  [body]
  (trap #(delete-messages body)))

(defroutes notificationagent-routes
  (GET "/" [] "Welcome to the notification agent!\n")
  (POST "/job-status" [:as {body :body}] (job-status body))
  (POST "/get-messages" [:as {body :body}] (messages body))
  (POST "/get-unseen-messages" [:as {body :body}] (unseen-messages body))
  (POST "/delete" [:as {body :body}] (delete body))
  (route/not-found "Unrecognized service path.\n"))

(defn site-handler [routes]
  (-> routes
      wrap-keyword-params
      wrap-nested-params))

(def app
  (site-handler notificationagent-routes))

(defn -main
  [& args]
  (def zkprops (cc-props/parse-properties "notificationagent.properties"))
  (def zkurl (get zkprops "zookeeper"))

  ;; Load the configuration from zookeeper.
  (cl/with-zk
    zkurl
    (when (not (cl/can-run?))
      (log/warn "THIS APPLICATION CANNOT RUN ON THIS MACHINE. SO SAYETH ZOOKEEPER.")
      (log/warn "THIS APPLICATION WILL NOT EXECUTE CORRECTLY.")
      (System/exit 1))
    (reset! props (cl/properties "notificationagent")))

  ;; Initialize the job-status service.
  (initialize-job-status-service)

  ;; Start the server.
  (log/warn @props)
  (log/warn (str "Listening on " (listen-port)))
  (jetty/run-jetty (site-handler notificationagent-routes) {:port (listen-port)}))

(ns notification-agent.core
  (:gen-class)
  (:use [clojure-commons.error-codes :only [trap]]
        [clojure-commons.lcase-params :only [wrap-lcase-params]]
        [clojure-commons.query-params :only [wrap-query-params]]
        [compojure.core]
        [ring.middleware keyword-params nested-params]
        [notification-agent.delete]
        [notification-agent.job-status]
        [notification-agent.notifications]
        [notification-agent.query]
        [notification-agent.seen]
        [slingshot.slingshot :only [try+]])
  (:require [compojure.route :as route]
            [clojure.tools.logging :as log]
            [notification-agent.config :as config]
            [notification-agent.db :as db]
            [ring.adapter.jetty :as jetty]))

(defn- job-status
  "Handles a job status update request."
  [body]
  (trap :job-status #(handle-job-status body)))

(defn- notification
  "Handles a generic notification request."
  [body]
  (trap :notification #(handle-notification-request body)))

(defn- delete
  "Handles a message deletion request."
  [params body]
  (trap :delete #(delete-messages params body)))

(defn- delete-all
  "Handles a request to delete all messages for a user."
  [params]
  (trap :delete-all #(delete-all-messages params)))

(defn- unseen-messages
  "Handles a query for unseen messages."
  [query]
  (trap :unseen-messages #(get-unseen-messages query)))

(defn- messages
  "Handles a request for a paginated message view."
  [query]
  (trap :messages #(get-paginated-messages query)))

(defn- count-msgs
  "Handles a request to count messages."
  [query]
  (trap :count-messages #(count-messages query)))

(defn- last-ten
  "Handles a request to get the most recent ten messages."
  [query]
  (trap :last-ten-messages #(last-ten-messages query)))

(defn- mark-seen
  "Handles a request to mark one or messages as seen."
  [body params]
  (trap :seen #(mark-messages-seen body params)))

(defn- mark-all-seen
  "Handles a request to mark all messages for a user as seen."
  [body]
  (trap :mark-all-seen #(mark-all-messages-seen body)))

(defn- add-system-notification
  "Handles a request to add a system notification."
  [body]
  (trap :add-system-notification #(handle-add-system-notif body)))

(defn- get-system-notification
  "Handles retrieving a system notification by uuid."
  [uuid]
  (trap :get-system-notificaiton #(handle-get-system-notif uuid)))

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
  (PUT "/system" [:as {body :body}] (add-system-notification body))
  (GET "/system/:uuid" [uuid :as {body :body }] (get-system-notification uuid))
  (route/not-found "Unrecognized service path.\n"))

(defn site-handler [routes]
  (-> routes
      wrap-keyword-params
      wrap-lcase-params
      wrap-nested-params
      wrap-query-params))

(def app
  (site-handler notificationagent-routes))

(defn- init-service
  []
  (db/define-database))

(defn load-config-from-file
  []
  (config/load-config-from-file)
  (init-service))

(defn load-config-from-zookeeper
  []
  (config/load-config-from-zookeeper)
  (init-service))

(defn -main
  [& _]
  (load-config-from-zookeeper)
  (initialize-job-status-service)
  (log/warn "Listening on" (config/listen-port))
  (jetty/run-jetty (site-handler notificationagent-routes) {:port (config/listen-port)}))

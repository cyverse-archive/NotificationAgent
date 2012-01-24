(ns notification-agent.core
  (:use [compojure.core]
        [ring.middleware keyword-params nested-params]
        [notification-agent.job-status])
  (:require [compojure.route :as route]
            [compojure.handler :as handler]))

(defroutes notificationagent-routes
  (GET "/" [] "Welcome to the notification agent!\n")
  (POST "/job-status" [:as {body :body}] (handle-job-status body))
  (route/not-found "Unrecognized service path.\n"))

(defn site-handler [routes]
  (-> routes
      wrap-keyword-params
      wrap-nested-params))

(def app
  (site-handler notificationagent-routes))

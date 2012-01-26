(ns notification-agent.common
 (:require [clojure.tools.logging :as log]
           [clojure-commons.json :as cc-json]))

(defn parse-body
  "Parses a JSON request body, throwing an IllegalArgumentException if the
   body can't be parsed."
  [body]
  (try
    (cc-json/body->json body)
    (catch Throwable t
      (throw (IllegalArgumentException. (str "invalid request body: " t))))))

(defn resp
  "Returns a value that Ring can use to generate a response."
  [status msg]
  {:status status
   :body msg})

(defn error-resp
  "Returns a value that Ring can use to generate a 400 response."
  [e]
  (log/error e "bad request")
  {:status 400
   :body (str (.getMessage e) "\n")
   :content-type "text/plain"})

(defn failure-resp
  "Returns a value that Ring can use to generate a 500 response."
  [e]
  (log/error e "internal error")
  {:status 500
   :body (str "Internal Error: " (.getMessage e) "\n")
   :content-type "text/plain"})

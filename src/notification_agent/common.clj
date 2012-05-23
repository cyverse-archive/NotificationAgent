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
  [status body]
  (log/debug (str "response:" body))
  {:status status
   :body body
   :headers {"Content-Type" "text/plain"}})

(defn json-resp
  "Returns a value that Ring can use to generate a JSON response."
  [status body]
  (log/debug (str "response:" body))
  {:status status
   :body body
   :headers {"Content-Type" "text/plain"}})

(defn error-resp
  "Returns a value that Ring can use to generate a 400 response."
  [e]
  (log/error e "bad request")
  {:status 400
   :body (str (.getMessage e) "\n")
   :headers {"Content-Type" "text/plain"}})

(defn failure-resp
  "Returns a value that Ring can use to generate a 500 response."
  [e]
  (log/error e "internal error")
  {:status 500
   :body (str "Internal Error: " (.getMessage e) "\n")
   :headers {"Content-Type" "text/plain"}})

(defn valid-email-addr
  "Validates an e-mail address."
  [addr]
  (and (not (nil? addr)) (re-matches #"^[^@ ]+@[^@ ]+$" addr)))

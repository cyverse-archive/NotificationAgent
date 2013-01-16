(ns notification-agent.common
  (:use [slingshot.slingshot :only [try+ throw+]])
  (:require [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [clojure-commons.error-codes :as ce]
            [clojure-commons.json :as cc-json]))

(defn parse-body
  "Parses a JSON request body, throwing an IllegalArgumentException if the
   body can't be parsed."
  [body]
  (try
    (cc-json/body->json body)
    (catch Throwable t
      (throw (IllegalArgumentException. (str "invalid request body: " t))))))

(defn validate-user
  "Validates the username that was passed in. Returns the username when valid."
  [user]
  (when (nil? user)
    (throw+ {:error_code ce/ERR_ILLEGAL_ARGUMENT
             :param      :user}))
  user)

(defn success-resp
  "Returns an empty success response."
  ([]
     (success-resp {}))
  ([m]
     {:status       200
      :body         (json/json-str (assoc m :success true))
      :content-type :json}))

(defn json-resp
  "Returns a value that Ring can use to generate a JSON response."
  [status body]
  (log/debug (str "response:" body))
  {:status       status
   :body         body
   :content-type :json})

(defn valid-email-addr
  "Validates an e-mail address."
  [addr]
  (and (not (nil? addr)) (re-matches #"^[^@ ]+@[^@ ]+$" addr)))

(defn string->long
  "Converts a string to a long integer."
  [s details exception-info-map]
  (try+
   (Long/parseLong s)
   (catch NumberFormatException e
     (throw+
      (merge {:error_code ce/ERR_ILLEGAL_ARGUMENT
              :details    details}
             exception-info-map)))))

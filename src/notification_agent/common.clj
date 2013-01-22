(ns notification-agent.common
  (:use [clojure.java.io :only [reader]]
        [slingshot.slingshot :only [try+ throw+]])
  (:require [cheshire.core :as cheshire]
            [clojure.tools.logging :as log]
            [clojure-commons.error-codes :as ce]
            [clojure-commons.json :as cc-json])
  (:import [java.io InputStream Reader]))

(defn parse-body
  "Parses a JSON request body, throwing an IllegalArgumentException if the
   body can't be parsed."
  [body]
  (try+
    (if (or (instance? InputStream body) (instance? Reader body))
      (cheshire/decode-stream (reader body))
      (cheshire/decode body))
    (catch Throwable t
      (throw+ {:error_code ce/ERR_INVALID_JSON
               :defails    (.getMessage t)}))))

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
      :body         (cheshire/encode (assoc m :success true))
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

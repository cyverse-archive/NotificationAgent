(ns notification-agent.common
  (:use [slingshot.slingshot :only [try+ throw+]])
  (:require [clojure.data.json :as json]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [clojure-commons.json :as cc-json]))

(defn parse-body
  "Parses a JSON request body, throwing an IllegalArgumentException if the
   body can't be parsed."
  [body]
  (try
    (cc-json/body->json body)
    (catch Throwable t
      (throw (IllegalArgumentException. (str "invalid request body: " t))))))

(defn success-resp
  "Returns an empty success response."
  []
  {:status       200
   :body         (json/json-str {:success true})
   :content-type :json})

(defn json-resp
  "Returns a value that Ring can use to generate a JSON response."
  [status body]
  (log/debug (str "response:" body))
  {:status       status
   :body         body
   :content-type :json})

(defn illegal-argument-resp
  "Produces a response indicating that an illegal argument was passed as a
   parameter."
  [type code param value]
  (let [body (json/json-str {:type  (string/upper-case (name type))
                             :code  (string/upper-case (name code))
                             :param param
                             :value value})]
    (log/warn "illegal argument received:" body)
    {:status       400
     :body         body
     :content-type :json}))

(defn- java-exception-json
  [e]
  (let [ename (.. e getClass getSimpleName)
        comps (filter (comp not string/blank?)
                      (string/split ename #"(?=\p{Upper})"))
        code  (string/join "_" (map string/upper-case comps))]
    (json/json-str {:code    code
                    :message (.getMessage e)})))

(defn error-resp
  "Returns a value that Ring can use to generate a 400 response."
  [e]
  (log/error e "bad request")
  {:status       400
   :body         (java-exception-json e)
   :content-type :json})

(defn failure-resp
  "Returns a value that Ring can use to generate a 500 response."
  [e]
  (log/error e "internal error")
  {:status       500
   :body         (java-exception-json e)
   :content-type :json})

(defn valid-email-addr
  "Validates an e-mail address."
  [addr]
  (and (not (nil? addr)) (re-matches #"^[^@ ]+@[^@ ]+$" addr)))

(defn string->long
  "Converts a string to a long integer."
  [s code exception-info-map]
  (try+
   (Long/parseLong s)
   (catch NumberFormatException e
     (throw+
      (merge {:type  :illegal-argument
              :code  code}
             exception-info-map)))))

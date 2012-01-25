(ns notification-agent.query
 (:use [notification-agent.config])
 (:require [clojure.data.json :as json]
           [clojure-commons.json :as cc-json]
           [clojure-commons.osm :as osm]
           [clojure.tools.logging :as log]))

(defn- format-query
  "Formats the query to be sent to the OSM.

   Parameters:
      query - the query that was sent to the notification agent."
  [query]
  (let [osm-query {:state.user (:user query) :state.deleted false}]
    (if (nil? (:seen query))
      osm-query
      (assoc osm-query :state.seen (:seen query)))))

(defn- query-osm
  "Queries the messages for the messages that the caller wants to see.

   Parameters:
     query - the query that was sent to the notification agent."
  [query]
  (json/read-json (osm/query notifications-osm (format-query query))))

;; TODO: make the code to limit the number of results work.
(defn- get-messages*
  [query]
  (let [limit (:limit query)
        results (query-osm query)]
    (log/warn (str "limit: " limit))
    (log/warn (str "(number? limit): " (number? limit)))
    (json/json-str
      (if (number? limit)
        (take-last limit results)
        results))))

(defn get-messages
  "Looks up messages in the OSM that may or may not have been seen yet.  The
   sender can specify that the query return either seen or unseen messages by
   specifying the 'seen' flag value in the request JSON.  The request is in
   the following format:

   {
       \"user\":  \"username\",
       \"seen\":  false,
       \"limit\": 50
   }

   Where both the 'seen' and 'limit' fields are optional.  If the 'seen' field
   is omitted then both seen and unseen messages will be returned.  If the
   'limit' field is omitted then all messages that satisfy the other criteria
   will be returned.

   Parameters:
     body - the request body"
  [body]
  (get-messages* (cc-json/body->json body)))

(defn get-unseen-messages
  "Looks up messages in the OSM that have not been seen yet.  All other search
   criteria being the same, this function is equivalent to calling get-messages
   and specifying a 'seen' flag of 'false'.

   Parameters:
     body - the request body"
  [body]
  (get-messages* (assoc (cc-json/body->json body) :seen false)))

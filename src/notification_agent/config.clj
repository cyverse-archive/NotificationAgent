(ns notification-agent.config
  (:use [clojure.string :only (split)])
  (:require [clojure-commons.osm :as osm]))

(def
  ^{:doc "The name of the properties file."}
  prop-file "notificationagent.properties")

(def props
  ^{:doc "The properites that have been loaded from the properties file."}
  (atom nil))

(defn osm-base
  "The base URL used to connect to the OSM."
  []
  (get @props "notificationagent.osm-base"))

(defn osm-jobs-bucket
  "The OSM bucket containing job status information."
  []
  (get @props "notificationagent.osm-jobs-bucket"))

(defn osm-notifications-bucket
  "Ths OSM bucket containing notifications."
  []
  (get @props "notificationagent.osm-notifications-bucket"))

(defn email-enabled
  "True if e-mail notifications are enabled."
  []
  (get @props "notificationagent.enable-email"))

(defn email-url
  "The URL used to connect to the mail service."
  []
  (get @props "notificationagent.email-url"))

(defn email-template
  "The template to use when sending e-mail notifications."
  []
  (get @props "notificationagent.email-template"))

(defn notification-recipients
  "The list of URLs to send notifications to."
  []
  (split (get @props "notificationagent.recipients") #","))

(defn listen-port
  "The port to listen to for incoming connections."
  []
  (Integer/parseInt (get @props "notificationagent.listen-port")))

(defn jobs-osm
  "The OSM client instance used to retrieve job status information."
  []
  (osm/create (osm-base) (osm-jobs-bucket)))

(defn notifications-osm
  "The OSM client instance used to store and retrieve notifications."
  []
  (osm/create (osm-base) (osm-notifications-bucket)))

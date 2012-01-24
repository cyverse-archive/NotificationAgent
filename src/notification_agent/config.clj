(ns notification-agent.config
  (:require [clojure-commons.props :as cc-props]
            [clojure-commons.osm :as osm]))

(def
  ^{:doc "The name of the properties file."}
  prop-file "notificationagent.properties")

(def props
  ^{:doc "The properites that have been loaded from the properties file."}
  (cc-props/load-properties-configuration prop-file))

(def osm-base
  ^{:doc "The base URL used to connect to the OSM."}
  (.getString props "iplantc.notificationagent.osm-base"))

(def osm-jobs-bucket
  ^{:doc "The OSM bucket containing job status information."}
  (.getString props "iplantc.notificationagent.osm-jobs-bucket"))

(def osm-notifications-bucket
  ^{:doc "Ths OSM bucket containing notifications."}
  (.getString props "iplantc.notificationagent.osm-notifications-bucket"))

(def email-enabled
  ^{:doc "True if e-mail notifications are enabled."}
  (.getString props "iplantc.notificationagent.enable-email"))

(def email-url
  ^{:doc "The URL used to connect to the mail service."}
  (.getString props "iplantc.notificationagent.email-url"))

(def email-template
  ^{:doc "The template to use when sending e-mail notifications."}
  (.getString props "iplantc.notificationagent.email-template"))

(def notification-recipients
  ^{:doc "The list of URLs to send notifications to."}
  (seq (.getStringArray props "iplantc.notificationagent.recipients")))

(def jobs-osm
  ^{:doc "The OSM client instance used to retrieve job status information."}
  (osm/create osm-base osm-jobs-bucket))

(def notifications-osm
  ^{:doc "The OSM client instance used to store and retrieve notifications."}
  (osm/create osm-base osm-notifications-bucket))

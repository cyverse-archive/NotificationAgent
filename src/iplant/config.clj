(ns iplant.config
  (:require [clojure-commons.props :as cc-props]
            [clojure-commons.osm :as osm]))

(def prop-file
  ^{:doc "The name of the file containing the properties."}
  "notificationagent.properties")

(def props
  ^{:doc "The properties extracted from the properties file."}
  (cc-props/parse-properties prop-file))

(def osm-base
  ^{:doc "The base URL used to connect to the OSM."}
  (get props "iplantc.notificationagent.osm-base"))

(def osm-jobs-bucket
  ^{:doc "The OSM bucket used to store job status information."}
  (get props "iplantc.notificationagent.osm-jobs-bucket"))

(def osm-notifications-bucket
  ^{:doc "The OSM bucket used to store notifications."}
  (get props "iplantc.notificationagent.osm-notifications-bucket"))

(def email-enabled
  ^{:doc "True if e-mail notifications are enabled."}
  (get props "iplantc.notificationagent.enable-email"))

(def email-url
  ^{:doc "The URL used to connect to the e-mail service."}
  (get props "iplantc.notificationagent.email-url"))

(def email-template
  ^{:doc "The name of the e-mail template to use."}
  (get props "iplantc.notificationagent.email-template"))

(def jobs-osm
  ^{:doc "The OSM client used to update job status information."}
  (osm/create osm-base osm-jobs-bucket))

(def notifications-osm
  %{:doc "The OSM client used to store and retrieve notifications."}
  (osm/create osm-base osm-notifications-bucket))

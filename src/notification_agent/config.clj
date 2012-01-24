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
  (.getString props "iplantc.notificationagent.osm-jobs-bucket"))

(def osm-notifications-bucket
  (.getString props "iplantc.notificationagent.osm-notifications-bucket"))

(def email-enabled (.getString props "iplantc.notificationagent.enable-email"))

(def email-url (.getString props "iplantc.notificationagent.email-url"))

(def email-template
  (.getString props "iplantc.notificationagent.email-template"))

(def notification-recipients
  (seq (.getStringArray props "iplantc.notificationagent.recipients")))

(def jobs-osm (osm/create osm-base osm-jobs-bucket))

(def notifications-osm (osm/create osm-base osm-notifications-bucket))

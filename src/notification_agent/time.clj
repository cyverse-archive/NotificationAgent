(ns notification-agent.time
 (:use [clj-time.core :only (default-time-zone now)]
       [clj-time.format :only (formatter parse unparse)])
 (:require [clojure.string :as str]))

(def date-formatter
  ^{:private true
    :doc "The date formatter that is used to format all timestamps."}
  (formatter "EEE MMM dd YYYY HH:mm:ss 'GMT'Z (z)" (default-time-zone)))

(def date-parser
  ^{:private true
    :doc "The date formatter that is used to parse all timestamps."}
  (formatter "EEE MMM dd YYYY HH:mm:ss 'GMT'Z" (default-time-zone)))

(defn- strip-zone-name
  "Strips the time zone name from a timestamp."
  [timestamp]
  (str/replace timestamp #"\s*\([^\)]*\)$" ""))

(defn current-time
  "Returns the current time, formatted in a similar manner to the default
   date and time format used by JavaScript."
  []
  (unparse date-formatter (now)))

(defn parse-timestamp
  "Parses a timestamp that is in a format similar to the default date and time
   format used by JavaScript.  According to the Joda Time API documentation,
   time zone names are not parseable.  These timestamps already contain the
   time zone offset, however, so the time zone names are redundant.  The
   solution is to strip the time zone name before attempting to parse the
   timestamp."
  [timestamp]
  (parse date-parser (strip-zone-name timestamp)))

(defn timestamp->millis
  "Converts a timestamp to the number of milliseconds since the epoch."
  [timestamp]
  (if (str/blank? timestamp) "" (.getMillis (parse-timestamp timestamp))))

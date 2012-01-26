(defproject notificationagent "1.0.0-SNAPSHOT"
  :description "Notification Agent v1.0.0"
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/data.json "0.1.1"]
                 [org.clojure/tools.logging "0.2.3"]
                 [compojure "1.0.1"]
                 [swank-clojure "1.4.0-SNAPSHOT"]
                 [org.iplantc/clojure-commons "1.1.0-SNAPSHOT"]
                 [clj-http "0.2.7"]
                 [clj-time "0.3.4"]
                 [ring/ring-jetty-adapter "1.0.1"]
                 [org.codehaus.jackson/jackson-core-asl "1.9.3"]]
  :dev-dependencies [[lein-ring "0.4.5"]
                     [swank-clojure "1.2.1"]]
  :ring {:handler notification-agent.core/app}
  :aot [notification-agent.core]
  :main notification-agent.core
  :repositories {"iplantCollaborative"
                 "http://projects.iplantcollaborative.org/archiva/repository/internal/"})

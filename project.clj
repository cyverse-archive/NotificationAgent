(defproject notificationagent "1.1.0-SNAPSHOT"
  :description "Notification Agent v1.1.0"
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
                     [swank-clojure "1.2.1"]
                     [lein-marginalia "0.7.0"]
                     [org.iplantc/lein-iplant-rpm "1.1.0-SNAPSHOT"]]
  :ring {:handler notification-agent.core/app}
  :iplant-rpm {:summary "iPlant Notification Agent"
               :release 1
               :provides "notificationagent"
               :dependencies ["iplant-service-config >= 0.1.0-4"]
               :config-files ["log4j.properties"]
               :config-path "conf"}
  :aot [notification-agent.core]
  :main notification-agent.core
  :repositories {"iplantCollaborative"
                 "http://projects.iplantcollaborative.org/archiva/repository/internal/"})

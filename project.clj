(defproject notificationagent "1.2.0-SNAPSHOT"
  :description "Notification Agent v1.2.0"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.clojure/data.json "0.1.2"]
                 [org.clojure/tools.logging "0.2.3"]
                 [compojure "1.0.2"]
                 [org.iplantc/clojure-commons "1.1.0-SNAPSHOT"]
                 [clj-http "0.4.0"]
                 [clj-time "0.4.2"]
                 [ring/ring-jetty-adapter "1.1.0"]
                 [org.codehaus.jackson/jackson-core-asl "1.9.5"]]
  :plugins [[lein-ring "0.6.4"]
            [swank-clojure "1.4.2"]
            [lein-marginalia "0.7.0"]
            [org.iplantc/lein-iplant-rpm "1.2.1-SNAPSHOT"]]
  :ring {:handler notification-agent.core/app
         :init notification-agent.core/load-configuration}
  :profiles {:dev {:resource-paths ["conf/test"]}}
  :extra-classpath-dirs ["conf/test"]
  :iplant-rpm {:summary "iPlant Notification Agent"
               :release 4
               :provides "notificationagent"
               :dependencies ["iplant-service-config >= 0.1.0-5"]
               :config-files ["log4j.properties"]
               :config-path "conf/main"}
  :aot [notification-agent.core]
  :main notification-agent.core
  :uberjar-exclusions [#"(?i)[.]sf"]
  :repositories {"iplantCollaborative"
                 "http://projects.iplantcollaborative.org/archiva/repository/internal/"})

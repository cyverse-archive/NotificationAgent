(defproject notificationagent "2.0.1-SNAPSHOT"
  :description "Notification Agent v1.2.0"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.logging "0.2.3"]
                 [cheshire "5.0.1"]
                 [compojure "1.0.2"]
                 [org.iplantc/clojure-commons "1.4.1-SNAPSHOT"]
                 [org.iplantc/kameleon "0.1.2-SNAPSHOT"]
                 [clj-http "0.5.5"]
                 [clj-time "0.5.0"]
                 [ring/ring-jetty-adapter "1.1.0"]
                 [org.codehaus.jackson/jackson-core-asl "1.9.5"]
                 [slingshot "0.10.3"]
                 [korma "0.3.0-beta10"]]
  :plugins [[lein-ring "0.6.4"]
            [swank-clojure "1.4.2"]
            [lein-marginalia "0.7.0"]
            [org.iplantc/lein-iplant-rpm "1.4.1-SNAPSHOT"]]
  :ring {:handler notification-agent.core/app
         :init notification-agent.core/load-config-from-file
         :port 31320}
  :profiles {:dev {:resource-paths ["conf/test"]}}
  :extra-classpath-dirs ["conf/test"]
  :iplant-rpm {:summary "iPlant Notification Agent"
               :provides "notificationagent"
               :dependencies ["iplant-service-config >= 0.1.0-5"]
               :config-files ["log4j.properties"]
               :config-path "conf/main"}
  :aot [notification-agent.core]
  :main notification-agent.core
  :uberjar-exclusions [#"(?i)[.]sf"]
  :repositories {"iplantCollaborative"
                 "http://projects.iplantcollaborative.org/archiva/repository/internal/"})

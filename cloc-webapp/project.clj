(defproject cloc-webapp "0.1.0-SNAPSHOT"
  :description "Web app"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.8.51"]
                 [ring/ring-core "1.5.0"]
                 [ring/ring-jetty-adapter "1.4.0"]
                 [compojure "1.5.1"]
                 [om "0.8.0-rc1"]
                 [hiccup "1.0.5"]]

  :plugins [[lein-npm "0.6.1"]]
  :main ^:skip-aot cloc-webapp.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})

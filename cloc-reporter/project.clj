(defproject cloc-reporter "0.1.0-SNAPSHOT"
  :description "cloc reporter"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [ring/ring-core "1.5.0"]
                 [ring/ring-jetty-adapter "1.4.0"]
                 [ring/ring-json "0.4.0"]
                 [compojure "1.5.1"]
                 [prismatic/schema "1.1.2"]
                 [com.apa512/rethinkdb "0.15.24"]]
  :main ^:skip-aot cloc-reporter.core
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                                  [cheshire "5.6.3"]
                                  [clj-http "3.2.0"]]}})
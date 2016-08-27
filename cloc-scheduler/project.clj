(defproject cloc-scheduler "0.1.0-SNAPSHOT"
  :description "cloc scheduler"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [com.taoensso/carmine "2.13.1"]
                 [com.apa512/rethinkdb "0.15.24"]
                 [docker-client "0.1.4"]]
  :main ^:skip-aot cloc-scheduler.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]]}})

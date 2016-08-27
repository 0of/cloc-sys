(defproject cloc-web "0.1.0-SNAPSHOT"
  :description "cloc web"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [ring/ring-core "1.5.0"]
                 [ring/ring-jetty-adapter "1.4.0"]
                 [ring/ring-json "0.4.0"]
                 [compojure "1.5.1"]
                 [selmer "1.0.7"]
                 [prismatic/schema "1.1.2"]
                 [com.apa512/rethinkdb "0.15.24"]]
  :main ^:skip-aot cloc-web.core
  :profiles {:uberjar {:aot :all}})

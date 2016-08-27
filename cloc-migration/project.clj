(defproject cloc-migration "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :main ^:skip-aot cloc-migration.core
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [ragtime "0.6.1"]
                 [com.apa512/rethinkdb "0.15.24"]
                 [resauce "0.1.0"]]
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]]}}
  :aliases {"migrate"  ["run" "-m" "cloc-migration.core/migrate"]
            "rollback" ["run" "-m" "cloc-migration.core/rollback"]})

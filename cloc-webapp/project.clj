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
                 [hiccup "1.0.5"]
                 [cljs-http "0.1.21"]
                 [clj-http "3.2.0"]
                 [cheshire "5.6.3"]]
  :plugins [[lein-cljsbuild "1.1.4"]]
  :cljsbuild {:builds [{:source-paths ["src-cljs/main"]
                        :compiler {:output-to "resources/public/js/main.js"
                                   :optimizations :none
                                   :output-dir "resources/public/"
                                   :source-map true}}
                       {:source-paths ["src-cljs/dash"]
                        :compiler {:output-to "resources/public/dash/js/dash.js"
                                   :optimizations :none
                                   :output-dir "resources/public/dash/"
                                   :source-map true}}]}
  :main ^:skip-aot cloc-webapp.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[org.clojure/tools.logging "0.3.1"]]}})

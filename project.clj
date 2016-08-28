(defproject cloc-sys "0.1.0-SNAPSHOT"
  :description "cloc sys"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]]
  :plugins [[lein-sub "0.3.0"]]
  :sub ["cloc-reporter" "cloc-scheduler" "cloc-web"])

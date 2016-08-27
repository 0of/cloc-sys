(ns cloc-web.core
  (:require [ring.adapter.jetty :as jetty]
            [compojure.core :refer :all]
            [ring.middleware.params :as params]
            [cloc-web.query-cloc :refer [get-svg-badge]])
  (:gen-class))

(defroutes app
  (GET "/:user/:repo/:branch/svg_badge" {params :params} (get-svg-badge params)))  

(defn -main
  [& args]
  (jetty/run-jetty app {:port 4679}))
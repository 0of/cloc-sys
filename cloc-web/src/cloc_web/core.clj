(ns cloc-web.core
  (:require [ring.adapter.jetty :as jetty]
            [compojure.core :refer :all]
            [ring.middleware.params :as params]
            [cloc-web.query-cloc :refer [get-svg-badge]]
            [cloc-web.users :refer [register]])          
  (:gen-class))

(defroutes app
  (GET "/:user/:repo/:branch/svg_badge" {params :params} (get-svg-badge params))
  (POST "/:user/:repo/register" {params :params} (register params)))  

(defn -main
  [& args]
  (jetty/run-jetty app {:port 4679}))
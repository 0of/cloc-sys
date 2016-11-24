(ns cloc-webapp.core
  (:require [ring.adapter.jetty :as jetty]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :refer [resource-response]])          
  (:gen-class))

(defroutes app
  ;; main page
  (GET  "/" [] (resource-response "index.html" {:root "public"}))
  (GET  "/dash" [] (resource-response "dash.html" {:root "public"}))
  (GET  "/login" [] (resource-response "login.html" {:root "public"}))
  (route/resources "/"))

(defn -main
  [& args]
  (jetty/run-jetty app {:port 8000}))
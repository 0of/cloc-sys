(ns cloc-webapp.core
  (:require [ring.adapter.jetty :as jetty]
            [compojure.core :refer :all]
            [ring.util.response :refer [resource-response]])          
  (:gen-class))

(defroutes app
  ;; main page
  (GET  "/" [] (resource-response "index.html" {:root "public"}))
  (GET  "/dash" [] (resource-response "dash.html" {:root "public"}))
  (GET  "/login" [] (resource-response "login.html" {:root "public"})))  

(defn -main
  [& args]
  (jetty/run-jetty app {:port 8080}))
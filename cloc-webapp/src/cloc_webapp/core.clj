(ns cloc-webapp.core
  (:require [ring.adapter.jetty :as jetty]
            [compojure.core :refer :all]
            [ring.middleware.params :as params]
            [compojure.handler :as handler])
  (:gen-class))

(defroutes app)
  ;; main page

(def handlers
  (-> (handler/site app)
      params/wrap-params))

(defn -main
  [& args]
  (jetty/run-jetty handlers {:port 8080}))
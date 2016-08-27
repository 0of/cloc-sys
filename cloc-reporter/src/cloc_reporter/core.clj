(ns cloc-reporter.core
  (:require [ring.adapter.jetty :as jetty]
            [compojure.core :refer :all]
            [compojure.handler :as handler]
            [ring.middleware.json :as json]
            [ring.middleware.params :as params]
            [cloc-reporter.submit :refer [submit fail]]
            [clojure.tools.logging :refer [info]])
  (:gen-class))

(defroutes app
  (POST "/submit/:task-id" req (submit req))
  (POST "/fail/:task-id" req (fail req)))

(def handlers
  (-> (handler/site app)
      (json/wrap-json-body {:keywords? true}) 
      params/wrap-params))

(defn -main
  [& args]
  (jetty/run-jetty handlers {:port 4678}))
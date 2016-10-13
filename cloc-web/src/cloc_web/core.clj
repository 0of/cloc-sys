(ns cloc-web.core
  (:require [ring.adapter.jetty :as jetty]
            [compojure.core :refer :all]
            [ring.middleware.params :as params]
            [ring.middleware.json :as json]
            [compojure.handler :as handler]
            [cloc-web.query-cloc :refer [get-svg-badge]]
            [cloc-web.users :refer [register update-display-lang]])          
  (:gen-class))

(defroutes app
  (GET "/:user/:repo/:branch/svg_badge" {params :params} (get-svg-badge params))
  (POST "/:user/:repo/register" {params :params} (register params))  
  (PATCH "/:user/:repo/display_lang" {params :params body :body} (update-display-lang params body)))

(def handlers
  (-> (handler/site app)
      (json/wrap-json-body {:keywords? true}) 
      params/wrap-params))

(defn -main
  [& args]
  (jetty/run-jetty handlers {:port 4679}))
(ns cloc-web.core
  (:require [ring.adapter.jetty :as jetty]
            [compojure.core :refer :all]
            [ring.middleware.params :as params]
            [ring.middleware.json :as json]
            [ring.middleware.cookies :as cookies]
            [compojure.handler :as handler]
            [cloc-web.query-cloc :refer [get-svg-badge]]
            [cloc-web.users :refer [register update-display-lang list-registered-repos]]  
            [cloc-web.auth :refer [auth auth-callback wrap-token wrap-user gen-session]])
  (:gen-class))

(defroutes app
  (GET "/:user/:repo/:branch/svg_badge" {params :params} (get-svg-badge params))

  (POST "/user/:repo/register" {params :params} (register params))  
  (PATCH "/user/:repo/display_lang" {params :params body :body} (update-display-lang params body))
  (GET "/user/registered_repos" {params :params} (list-registered-repos params))

  (GET "/github/auth" auth)
  (GET "/github/auth_callback" {params :params} (auth-callback params)))

(def handlers
  (-> (handler/site app)
      (json/wrap-json-body {:keywords? true})
      wrap-user
      wrap-token
      cookies/wrap-cookies
      params/wrap-params))

(defn -main
  [& args]
  (jetty/run-jetty handlers {:port 4679}))
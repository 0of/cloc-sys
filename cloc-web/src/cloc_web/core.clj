(ns cloc-web.core
  (:require [ring.adapter.jetty :as jetty]
            [compojure.core :refer :all]
            [ring.middleware.params :as params]
            [ring.middleware.json :as json]
            [ring.middleware.cookies :as cookies]
            [compojure.handler :as handler]
            [cloc-web.query-cloc :refer [get-svg-badge render-png-badge]]
            [cloc-web.users :refer [register update-display-lang me is_login]]  
            [cloc-web.auth :refer [auth auth-callback wrap-token wrap-user]])
  (:gen-class))

(defroutes app
  (GET "/:user/:repo/:branch/svg_badge" {params :params} (get-svg-badge params))
  (GET "/:user/:repo/:branch/png_badge" {params :params} (render-png-badge params))

  (context "/user" []

    (context "/:repo" []
      (POST "/register" {params :params} (register params))  
      (PATCH "/display_lang" {params :params body :body} (update-display-lang params body)))
    ;  (GET "/" {params :param} (get-repo params)))

    (GET "/me" {params :params} (me params))    
    (GET "/is_login" {params :params} (is_login params)))

  (context "/github" []
    (GET "/auth" auth)
    (GET "/auth_callback" {params :params} (auth-callback params))))

(def handlers
  (-> (handler/site app)
      json/wrap-json-response
      (json/wrap-json-body {:keywords? true})
      wrap-user
      wrap-token
      cookies/wrap-cookies
      params/wrap-params))

(defn -main
  [& args]
  (jetty/run-jetty handlers {:port 4679}))
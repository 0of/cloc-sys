(ns cloc-webapp.core
  (:require [ring.adapter.jetty :as jetty]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [ring.util.response :refer [resource-response header]]
            [cloc-webapp.auth :refer [auth auth-callback debug-auth]])       
  (:gen-class))

(defonce api-server "http://localhost:9000/api")

(defroutes app
  ;; main page
  (GET  "/" [] (resource-response "index.html" {:root "public"}))
  (GET  "/dash" [] (resource-response "dash.html" {:root "public"}))
  (GET  "/login" [] (resource-response "login.html" {:root "public"}))

  (GET "/debug" [] (debug-auth))

  (context "/github" []
    (GET "/auth" req (auth req))
    (GET "/auth_callback" req (auth-callback req)))

  (route/resources "/"))

(defn api-server-handler
  [f]
  (fn [request]
    (f (assoc request :api-server api-server))))

(def handlers
  (-> (handler/site app)
      api-server-handler))

(defn -main
  [& args]
  (jetty/run-jetty handlers {:port 8000}))
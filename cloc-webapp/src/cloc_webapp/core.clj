(ns cloc-webapp.core
  (:require [ring.adapter.jetty :as jetty]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [ring.util.response :refer [resource-response]]
            [cloc-webapp.auth :refer [auth auth-callback]])          
  (:gen-class))

(defroutes app
  ;; main page
  (GET  "/" [] (resource-response "index.html" {:root "public"}))
  (GET  "/dash" [] (resource-response "dash.html" {:root "public"}))
  (GET  "/login" [] (resource-response "login.html" {:root "public"}))

  (context "/github" []
    (GET "/auth" req (auth req))
    (GET "/auth_callback" req (auth-callback req)))

  (route/resources "/"))

(defn api-server-handler
  [f]
  (fn [request]
    (f (assoc request :api-server "http://localhost:4679"))))

(def handlers
  (-> (handler/site app)
      api-server-handler))

(defn -main
  [& args]
  (jetty/run-jetty handlers {:port 8000}))
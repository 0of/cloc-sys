(ns cloc-webapp.core
  (:require [ring.adapter.jetty :as jetty]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [ring.util.response :refer [resource-response header]]
            [cloc-webapp.auth :refer [auth auth-callback debug-auth]]       
            [clj-http.client :as client]
            [hiccup.core :refer [html]]
            [hiccup.element :refer [javascript-tag]]
            [cheshire.core :refer [generate-string]])
  (:gen-class))

(defonce api-server "http://localhost:9000/api")

(defn- template
  [self]
  (html [:body
              [:div {:id "content"}]
              (include-js "http://cdn.bootcss.com/react/0.14.0-rc1/react.js")
              (include-js "goog/base.js")
              (include-js "js/main.j")
              (javascript-tag (format "(function() {window.cloc_me=\"%s\"})()" (generate-string self)))
              (javascript-tag "goog.require(\"cloc_webapp.core\");")]))           

(defn- render-dash
  [req]
  (if-let [session (get-in request [:cookies "session_id"])]
    (if-let [self (client/get (format "%s/users/me" api-server) 
                        {:headers {"authorization" (format "Bearer %s" session)}})]
      (template self)
      {:status 401 :body "access denied (authorization)"})
    {:status 401 :body "access denied (authorization)"}))

(defroutes app
  ;; main page
  (GET  "/" [] (resource-response "index.html" {:root "public"}))
  (GET  "/dash" req (render-dash req))
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
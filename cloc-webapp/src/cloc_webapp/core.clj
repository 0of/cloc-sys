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
            [hiccup.page :refer [include-js include-css]]
            [cheshire.core :refer [generate-string]]
            [clojure.tools.logging :refer [info]])
  (:gen-class))

(defonce api-server "http://localhost:9000/api")

(defn- template
  [self]
  (html [:head
            (include-css "https://maxcdn.bootstrapcdn.com/bootswatch/3.3.7/cosmo/bootstrap.min.css")]
        [:body
            (include-js "http://cdn.bootcss.com/react/0.14.0-rc1/react.js")
            (include-js "dash/goog/base.js")
            (include-js "dash/js/dash.js")
            (javascript-tag (format "(function() {window.cloc_me=%s})()" (generate-string self)))
            (javascript-tag "goog.require(\"cloc_webapp.dash\");")]))           

(defn- render-dash
  [req]
  (if-let [session (get-in req [:cookies "session_id"])]
    (let [resp (client/get (format "%s/user/me" api-server) 
                        {:headers {"authorization" (format "Bearer %s" (:value session))}
                         :throw-exceptions false})]
      (if (= 200 (:status resp))
        (template (:body resp))
        {:status 401 :body "access denied (authorization)"}))
    {:status 401 :body "access denied (authorization)"}))

(defroutes app
  ;; main page
  (GET  "/" [] (resource-response "index.html" {:root "public"}))
  (GET  "/dash" req (render-dash req))

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
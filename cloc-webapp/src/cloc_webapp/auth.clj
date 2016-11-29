(ns cloc-webapp.auth
  (:require [clj-http.client :as client]
            [cheshire.core :refer [parse-string]]
            [clojure.tools.logging :refer [info]]))

(defn auth
  [{api-server :api-server}]
  (if-let [link (-> format "%s/github/auth" api-server) 
                (client/get {:accept :json})
                get-in [:body :link]]
    {:status 302 :headers {"Location" link} :body ""}
    {:status 401 :body "access denied (authorization)"}))

(defn auth-callback 
  [{api-server :api-server params :params}]
  (if-let [token (-> (format "%s?%s" api-server (ring.util.codec/form-encode params))
                     (client/get {:accept :json})
                     get-in [:body :token])]
    {:status 302 :headers {"Location" "/dash"} :body "" :cookies {"session_id" {:value token}}}
    {:status 401 :body "access denied (authorization)"}))
  


(ns cloc-webapp.auth
  (:require [clj-http.client :as client]
            [cheshire.core :refer [parse-string]]))

(defn auth
  [{api-server :api-server}]
  (if-let [link (-> (format "%s/github/auth" api-server) 
                    (client/get {:accept :json})
                    (get-in [:body :link]))]
    {:status 302 :headers {"Location" link} :body ""}
    {:status 401 :body "access denied (authorization)"}))

(defn auth-callback 
  [{api-server :api-server params :params}]
  (if-let [token (-> (format "%s?%s" api-server (ring.util.codec/form-encode params))
                     (client/get {:accept :json})
                     (get-in [:body :token]))]
    {:status 302 :headers {"Location" "/dash"} :body "" :cookies {"session_id" {:value token}}}
    {:status 401 :body "access denied (authorization)"}))
  
(defn debug-auth
  []
  {:status 200 :body "" :cookies {"session_id" {:value "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE0ODM3MDcwNDAsImlhdCI6MTQ4MTI4Nzg0MCwiaXNzIjoiZGVidWcifQ.cvC0hLKg9G3j0RGzfeC7O1OuH4N2084e-dUHjPH-c-s"}}})

(ns cloc-webapp.auth
  (:require [schema.core :as s]
            [clj-http.client :as client]
            [cheshire.core :refer [parse-string]]
            [clj-time.core :refer [now plus days]]   
            [clojure.tools.logging :refer [info]]))

(defn auth
  [{api-server :api-server}]
  (if-let [link (get-in (client/get (format "%s/github/auth" api-server) [:body :link]))]
    {:status 302 :headers {"Location" link} :body ""}
    {:status 401 :body "access denied (authorization)"}))

(defn auth-callback 
  [{api-server :api-server params :params}])
  
  


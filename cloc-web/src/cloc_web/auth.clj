(ns cloc-web.auth
  (:require [schema.core :as s]
            [clj-http.client :as client]
            [cheshire.core :refer [parse-string]]
            [clj-jwt.core :refer [jwt to-str sign verify str->jwt]]
            [clj-time.core :refer [now plus days]]))     

(defonce ^:const ClientID "")
(defonce ^:const ^:private ClientSecret "")

(defonce ^:const ^:private JwtSecret "40e7-4a66")

(defn claim
  [access-token expires]
  (let [now (now)
        expires (plus now expires)]
    {:exp expires
     :iat now
     :iss access-token}))

(defn jwt-token
  [access-token {:keys [expires] :as opts}]
  (-> (claim access-token opts)
      jwt
      (sign :HS256 JwtSecret)
      to-str))

(defn auth
  []
  (let [params {:client_id ClientID
                :state (rand-int Integer/MAX_VALUE) 
                :scope (clojure.string/join " " ["user:email" "public_repo" "read:repo_hook"])}]
    {:link (format "https://github.com/login/oauth/authorize?%s" (ring.util.codec/form-encode params))}))

(defn auth-callback
  [{:keys [state code]}]
  (if code
    (let [resp (client/post "https://github.com/login/oauth/access_token" {:form-params {:client_id ClientID
                                                                                         :client_secret ClientSecret
                                                                                         :code code
                                                                                         :state state}                                                                            
                                                                           :accept :json})]
      (if-let [token (-> resp
                         :body          
                         (parse-string true)
                         :access_token)]
        {:status 200 :cookies {"session_id" {:value (jwt-token token {:expires (-> 28 days)})}}}
        {:status 401}))

    {:status 200}))    
(ns cloc-web.auth
  (:require [schema.core :as s]
            [clj-http.client :as client]
            [cheshire.core :refer [parse-string]]
            [clj-jwt.core :refer [jwt to-str sign str->jwt]]
            [clj-time.core :refer [now plus days]]   
            [clojure.tools.logging :refer [info]]))  

(defonce ^:const ClientID "")
(defonce ^:const ^:private ClientSecret "")

(defonce ^:const ^:private JwtSecret "40e7-4a66")

(defn- unauthorized
  []
  {:status 401
   :body "access denied (authorization)"})

(defn claim
  [access-token expires]
  (let [now (now)
        expires (plus now expires)]
    {:exp expires
     :iat now
     :iss access-token}))

(defn jwt-token
  [access-token {:keys [expires] :as opts}]
  (-> (claim access-token expires)
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
        {:status 200 :body {:token (jwt-token token {:expires (-> 28 days)})}}
        {:status 401}))

    {:status 200}))

(defn jwt-decode-session
  [session]
  (try 
    (some-> session
            str->jwt
            :claims
            :iss)       
    (catch Exception e
      nil)))

(defn wrap-user
  [handler]
  (fn [request]
    (if (clojure.string/starts-with? (:uri request) "/user")
      (if-let [token (:token request)]  
        (let [resp (if (= token "debug") {:body {:login "test-user"} :status 200}  
                    (client/get "https://api.github.com/user" {:headers {"Authorization" (format "Bearer %s" token)}
                                                               :accept :json}))]
          (if (= (:status resp) 200)
            (handler (assoc-in request [:params :user] (get-in resp [:body :login])))    
            (unauthorized)))

        (unauthorized))
      (handler request))))

(defn wrap-token
 [handler]
 (fn [request]
   (if-let [session (get-in request [:headers "authorization"] "")]
     (if-let [token (-> session
                        (clojure.string/replace "Bearer " "")
                        jwt-decode-session)]
       (handler (assoc request :token token))
       (handler request))
     (handler request))))

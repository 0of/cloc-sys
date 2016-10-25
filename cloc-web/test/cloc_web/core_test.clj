(ns cloc-web.core-test
  (:require [clojure.test :refer :all]
            [cloc-web.core :refer :all]
            [ring.adapter.jetty :as jetty]
            [rethinkdb.query :as r]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [cloc-web.auth :refer [jwt-token]]
            [clj-time.core :refer [days]])
  (:import [java.io Closeable]))

(defonce ^:const port 50002)
(defonce ^:const url "http://localhost:50002")

(def db-conn (atom nil))

(defrecord Server [server]
  Closeable
  (close [this] (.stop server)))

(defn setup-server
 [f]
 (with-open [_ (->Server (jetty/run-jetty #'handlers {:port port :join? false}))
             conn (r/connect :host "127.0.0.1" :port 28015 :db "cloc")]
   (reset! db-conn conn)
   (f)))

(defn setup-each
  [f]
  (f)
  (-> (r/table "users")
      r/delete
      (r/run @db-conn)))

(use-fixtures :once setup-server)
(use-fixtures :each setup-each)

(deftest register-with-unauthorized-user
  (is 401 (:status (client/post (str url "/user/repo/register") {:accept :json
                                                                 :throw-exceptions false}))))

(deftest register-with-authorized-user
  (with-redefs [clj-http.client/get (fn [& param] {:status 200
                                                   :body {:login "0of"}})]
    (is 200 (:status (client/post (str url "/user/repo/register") {:accept :json
                                                                   :throw-exceptions false
                                                                   :cookies {"session_id" {:discard true :value (jwt-token "debug_token" {:expires (-> 28 days)})}}})))))

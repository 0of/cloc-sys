(ns cloc-webhook.core-test
  (:require [clojure.test :refer :all]
            [cloc-webhook.core :refer :all]
            [ring.adapter.jetty :as jetty]
            [byte-streams :refer [to-byte-arrays print-bytes]]   
            [taoensso.carmine :as car :refer [wcar]]
            [clojure.java.io :as io] 
            [clj-http.client :as client])
  (:import [java.io Closeable]))

(defonce ^:const port 50002)

(defrecord Server [server]
  Closeable
  (close [this] (.stop server)))

(defn setup-each
  [f]
  (f)
  (car/wcar (:redis-spec spec) (car/flushdb)))

(defn setup-server
 [f]
 (with-open [_ (->Server (jetty/run-jetty #'app {:port port :join? false}))]
   (f)))

(use-fixtures :once setup-server)
(use-fixtures :each setup-each)

(deftest test-push-event-received
  (let [data (to-byte-arrays (io/file (io/resource "fixtures/push_ev.json")))]
    (client/post
      "http://localhost:50002/repo/hooks"
      {:content-type :json
       :headers {"X-GitHub-Event" "push"
                 "X-Hub-Signature" (hmac data)
                 "X-GitHub-Delivery" "72d3162e-cc78-11e3-81ab-4c9367dc0958"}          
       :as :json
       :throw-exceptions false
       :body data}))

  (let [[target task-id] (car/wcar (:redis-spec spec) (car/zrange "scheduling" 0 0 "withscores"))]
   (are [l r] (= l r)
     target "foo/bar/master"
     task-id "1"))) 
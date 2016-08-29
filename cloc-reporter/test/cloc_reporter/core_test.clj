(ns cloc-reporter.core-test
  (:require [clojure.test :refer :all]
            [cloc-reporter.core :refer :all]
            [ring.adapter.jetty :as jetty]
            [rethinkdb.query :as r]
            [clj-http.client :as client]
            [cheshire.core :as cheshire])
  (:import [java.io Closeable]))

(defonce ^:const port 50001)

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
  (-> (r/table "active_tasks")
      r/delete     
      (r/run @db-conn)) 
  (-> (r/table "result")
      r/delete     
      (r/run @db-conn))) 

(use-fixtures :once setup-server)
(use-fixtures :each setup-each)

(defmacro scenario [aside & body] `(do ~@body))

(deftest test-submit-ok
  (scenario "task"
    (-> (r/table "active_tasks")
        (r/insert {:id 1
                   :target "user/repo/master"
                   :container_id 1 
                   :state "running"})
        (r/run @db-conn)))

  (client/post
    "http://localhost:50001/submit/1"
    {:content-type :json
     :as :json
     :throw-exceptions false
     :body (cheshire/generate-string {:user "user"
                                      :repo "repo"
                                      :branch "master"
                                      :sum {:total 2394
                                            :code 1891}
                                      :langs [{:lang "c++" :total 2394 :code 1891}]})})

  (are [l r] (= l r)
    "finished" (-> (r/table "active_tasks")
                   (r/get 1)
                   (r/run @db-conn)
                   :state)
    "user/repo/master" (-> (r/table "result")
                           (r/get "user/repo/master")
                           (r/run @db-conn)
                           :id)))


(ns cloc-scheduler.active-tasks-test
  (:require [clojure.test :refer :all]
            [cloc-scheduler.active-tasks :refer :all]
            [rethinkdb.query :as r]
            [docker-client.core :as docker]
            [clojure.core.async :refer [poll!]]))       

(def spec (atom nil))

(defn setup-spec
 [f]
 (with-open [db-conn (r/connect :host "127.0.0.1" :port 28015 :db "cloc")] 
   (reset! spec {:redis-spec {:pool {}
                              :spec {:host "127.0.0.1"
                                     :port 6379}}
                 :db-conn db-conn 
                 :docker-client nil})

   (with-redefs [docker/start-container! (fn [& args] args)
                 docker/create-container! (fn [& args] args)]         
     (f))))

(defn setup-each
  [f]
  (f)
  (-> (r/table table)
      r/delete     
      (r/run (:db-conn @spec))))      

(use-fixtures :once setup-spec)
(use-fixtures :each setup-each)

(defmacro scenario [aside & body] `(do ~@body))

(deftest test-try-start-existed-target
  (scenario "setup existed target"
      (-> (r/table table)
          (r/insert {:id 1
                     :target "target"
                     :container_id ""
                     :state "pending"})
          (r/run (:db-conn @spec))))

  (is
    (= [:ok :pending]
       (try-start-task @spec "target" 2))))  

(deftest test-try-start-non-existed-target
 (are [l r] (= l r)
   [:ok :running] (try-start-task @spec "target" 1)
   "running" (-> (r/table table)
                 (r/get 1)
                 (r/run (:db-conn @spec))
                 :state)))

(deftest test-try-start-same-running-target
  (scenario "setup running task"
    (-> (r/table table)
        (r/insert {:id 1
                   :target "target"
                   :container_id ""
                   :state "running"})
        (r/run (:db-conn @spec))))

  (are [l r] (= l r)
   [:ok :pending] (try-start-task @spec "target" 2)
   "pending" (-> (r/table table)
                 (r/get 2)
                 (r/run (:db-conn @spec))
                 :state)))

(deftest test-try-start-pending-no-records
  (is (=
       [:error :not-found]
       (try-start-pending-task @spec))))

(deftest test-try-start-pending-one-running-record
  (scenario "setup running task"
    (-> (r/table table)
        (r/insert {:id 1
                   :target "target"
                   :container_id ""
                   :state "running"})
        (r/run (:db-conn @spec)))) 

  (is (=
       [:error :not-found]
       (try-start-pending-task @spec))))

(deftest test-try-start-pending-one-record
  (scenario "setup pending task"
    (-> (r/table table)
        (r/insert {:id 1
                   :target "target"
                   :container_id ""
                   :state "pending"})
        (r/run (:db-conn @spec)))) 

  (are [l r] (= l r)
   [:ok :running] (try-start-pending-task @spec)
   "running" (-> (r/table table)
                 (r/get 1)
                 (r/run (:db-conn @spec))
                 :state)))

(deftest test-try-start-pending-multi-records
  (scenario "setup several pending tasks"
    (-> (r/table table)
        (r/insert [{:id 1
                    :target "target"
                    :container_id ""
                    :state "pending"}
                   {:id 2
                    :target "target-2"
                    :container_id ""
                    :state "pending"}])
        (r/run (:db-conn @spec)))) 

  (letfn [(is-running? [{state :state}] (= state "running"))]
    (are [l r] (= l r)
     [:ok :running] (try-start-pending-task @spec)
     1 (count (filter is-running? (-> (r/table table)
                                      (r/pluck :state)              
                                      (r/run (:db-conn @spec))))))))

(deftest test-register-running-changes
  (let [chan (register-running-changes (:db-conn @spec))]
    (scenario "setup running task"
      (-> (r/table table)
          (r/insert {:id 1
                     :target "target"
                     :container_id ""
                     :state "running"})
          (r/run (:db-conn @spec)))  

      (-> (r/table table)
          (r/get 1)                   
          (r/update {:state "finished"})
          (r/run (:db-conn @spec))))

    (is (some? (poll! chan)))))
(ns cloc-scheduler.core-test
  (:require [clojure.test :refer :all]
            [cloc-scheduler.core :refer :all]
            [taoensso.carmine :as car :refer [wcar]]
            [docker-client.core :as docker]
            [clojure.core.async :refer [poll! chan put!]]
            [cloc-scheduler.active-tasks :as active-tasks])       
  (:import [java.util.concurrent.atomic AtomicInteger]))

(def spec (atom nil))

(defn setup-spec
 [f]
 (reset! spec {:redis-spec {:pool {}
                            :spec {:host "127.0.0.1"
                                   :port 6379}}
               :db-conn nil
               :docker-client nil})

 (with-redefs [docker/start-container! (fn [& args] args)
               docker/create-container! (fn [& args] args)] 
   (f)))

(defn setup-each
  [f]
  (f)
  (car/wcar (:redis-spec spec) (car/flushdb)))

(use-fixtures :once setup-spec)
(use-fixtures :each setup-each)

(defmacro scenario [aside & body] `(do ~@body))

(deftest test-schedule-tasks-chan-poll
  (let [task-chan (chan)
        counter (atom 0)]      
    (scenario "put one record into channel"
      (put! task-chan {:id 1}))
   
    (with-redefs [active-tasks/try-start-pending-task (fn [_] [:ok :running])]
      (are [l r] (= l r)
        true (check-and-schedule-tasks (AtomicInteger.) task-chan @spec counter)
        1 @counter))))

(deftest test-schedule-tasks-max-running-count-exceeded
  (let [task-chan (chan)
        counter (atom running-max)]      
    (scenario "put one record into channel"
      (put! task-chan {:id 1}))
   
    (with-redefs [active-tasks/try-start-pending-task (fn [_] [:ok :running])]
      (are [l r] (= l r)
        nil (check-and-schedule-tasks (AtomicInteger.) task-chan @spec counter)))))

(deftest test-schedule-tasks-running-pending-failed
  (let [task-chan (chan)
        counter (atom 0)]      
    (scenario "put one record into channel"
      (put! task-chan {:id 1}))
   
    (with-redefs [active-tasks/try-start-pending-task (fn [_] [:error ""])]
      (are [l r] (= l r)
        nil (check-and-schedule-tasks (AtomicInteger.) task-chan @spec counter)))))

(deftest test-schedule-tasks-no-scheduling-flag
  (let [task-chan (chan)
        counter (atom 0)
        scheduling-flag (AtomicInteger.)] 
    (is (not (check-and-schedule-tasks scheduling-flag task-chan @spec counter)))))

(deftest test-schedule-tasks-scheduling-flag-max-running-count-exceeded
  (let [task-chan (chan)
        counter (atom running-max)
        scheduling-flag (AtomicInteger. 1)] 
    (are [l r] (= l r)
      ;; processed even not start a task   
      true (check-and-schedule-tasks scheduling-flag task-chan @spec counter)  
      0 (.get scheduling-flag))))                  

(deftest test-schedule-tasks-scheduling-flag-empty-queue
  (let [task-chan (chan)
        counter (atom 0)
        scheduling-flag (AtomicInteger. 1)]

    (are [l r] (= l r)
      ;; processed even not start a task   
      true (check-and-schedule-tasks scheduling-flag task-chan @spec counter)  
      0 (.get scheduling-flag))))   

(deftest test-schedule-tasks-scheduling-start-ok
  (let [task-chan (chan)
        counter (atom 0)
        scheduling-flag (AtomicInteger. 1)]
    (scenario "queue task request with task-id 1"
      (car/wcar (:redis-spec spec) (car/zadd "scheduling" 1 "target")))  

    (with-redefs [active-tasks/try-start-task (fn [& args] [:ok :running])]
      (are [l r] (= l r)
        ;; processed even not start a task   
        true (check-and-schedule-tasks scheduling-flag task-chan @spec counter)  
        1 @counter
        0 (.get scheduling-flag)))))                            

(deftest test-schedule-tasks-scheduling-start-pending
  (let [task-chan (chan)
        counter (atom 0)
        scheduling-flag (AtomicInteger. 1)]
    (scenario "queue task request with task-id 1"
      (car/wcar (:redis-spec spec) (car/zadd "scheduling" 1 "target")))  

    (with-redefs [active-tasks/try-start-task (fn [& args] [:ok :pending])]
      (are [l r] (= l r)
        ;; processed even not start a task   
        true (check-and-schedule-tasks scheduling-flag task-chan @spec counter)  
        0 @counter
        0 (.get scheduling-flag)))))

(deftest test-schedule-tasks-scheduling-several-queued
  (let [task-chan (chan)
        counter (atom 0)
        scheduling-flag (AtomicInteger. 1)]
    (scenario "queue task request with task-id 1"
      (car/wcar (:redis-spec spec) (car/zadd "scheduling" 1 "target"))  
      (car/wcar (:redis-spec spec) (car/zadd "scheduling" 2 "target-2"))) 

    (with-redefs [active-tasks/try-start-task (fn [& args] [:ok :running])]
      (are [l r] (= l r)
        ;; processed even not start a task   
        true (check-and-schedule-tasks scheduling-flag task-chan @spec counter) 
        1 @counter
        1 (.get scheduling-flag)))))  
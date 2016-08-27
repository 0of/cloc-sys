(ns cloc-scheduler.core
  (:gen-class)
  (:require [taoensso.carmine :as car :refer [wcar]]
            [rethinkdb.query :as r]
            [docker-client.core :as docker]
            [docker-client.support.rest :as rest]
            [cloc-scheduler.active-tasks :as active-tasks]
            [clojure.core.async :refer [poll!]])
  (:import [java.util.concurrent.atomic AtomicInteger]
           [java.lang.Thread]
           [java.io Closeable]))

(defonce ^:const running-max 4)

(defrecord Listener [listener]
  Closeable
  (close [this] (car/close-listener listener)))

;; cancellation set -> redis set
;; scheduling task queue -> redis sorted set
;; pending task queue -> redis sorted set

(defn- cancel-task
  [{:keys [redis-spec db-conn docker-client] :as spec} counter]
  (let [task (car/wcar redis-spec (car/srandmember "cancellations"))
        r (active-tasks/try-cancel-task spec task)]

    (case r 
      [:ok :running]
      (swap! counter dec)

      [:error :not-found]
      ;; cancel queued task
      (car/wcar redis-spec (car/zremrangebyscore "scheduling" task task)))

    ;; remove cancellation
    (car/wcar redis-spec (car/srem "cancellations" task))))

(defn- run-task
  [{:keys [redis-spec db-conn docker-client] :as spec} counter]
  (let [running-count @counter
        ;; target -> user/repo/branch
        ;; task-id -> a number
        [target task-id] (car/wcar redis-spec (car/zrange "scheduling" 0 0 "withscores"))]

    ;; run container
    (when (and task-id (< running-count running-max))

      (let [[status state] (active-tasks/try-start-task spec target task-id)]
        (when (= :ok status)
          (when (= :running state)
            (swap! counter inc))

          ;; remove from scheduling queue
          (car/wcar redis-spec (car/zremrangebyscore "scheduling" task-id task-id)))))))

(defn- run-pending-task
  "return true if processed"
  [{:keys [redis-spec db-conn docker-client] :as spec} counter]
  (when (and 
          (< @counter running-max) 
          (= [:ok :running] (active-tasks/try-start-pending-task spec)))
    (swap! counter inc)
    true))

(defn- check-and-cancel-task
  [cancellation-flag scheduling-flag spec counter]
  (when (.compareAndSet cancellation-flag 1 0)
    ;; some tasks need to be cancelled
    (cancel-task spec counter)  

    ;; check if has pending task need to be cancelled
    (when (> (car/wcar (:redis-spec spec) (car/scard "cancellations")) 0)
      (.set cancellation-flag 1))

    (when (< @counter running-max)
      (.set scheduling-flag 1))

    ;; processed
    true))

(defn check-and-schedule-tasks
  [scheduling-flag new-task-chain spec counter]
  (if (some? (poll! new-task-chain))

    (run-pending-task spec counter)
    ;; if no pending tasks need to run check scheduling queue
    (when (.compareAndSet scheduling-flag 1 0)
      ;; schedule task to run
      (run-task spec counter)

      (when (and (< @counter running-max)
                 (> (car/wcar (:redis-spec spec) (car/zcard "scheduling")) 0))
        (.set scheduling-flag 1))

      ;; processed
      true)))

(defn -main
  [& args]
  (let [redis-spec {:pool {}
                    :spec {:host "127.0.0.1"
                           :port 6379}}
        scheduling-flag (AtomicInteger.)
        cancellation-flag (AtomicInteger.)
        task-counter (atom 0)
        docker-client (docker/client {:uri "http://localhost:4343"})]

    (with-open [db-conn (r/connect :host "127.0.0.1" :port 28015 :db "cloc")
                _ (->Listener (car/with-new-pubsub-listener redis-spec
                                {"task-queued" #(.set scheduling-flag 1)}
                                (car/subscribe "task-queued")))]

      ;; 
      (active-tasks/terminate-running-tasks db-conn docker-client)

      ;; register rethinkdb change
      (let [change-chan (active-tasks/register-running-changes db-conn)
            spec {:redis-spec redis-spec
                  :db-conn db-conn 
                  :docker-client docker-client}] 

        (loop []
          (if (and ;(not (check-and-cancel-task cancellation-flag scheduling-flag spec task-counter))
                  (not (check-and-schedule-tasks scheduling-flag change-chan spec task-counter)))
            ;; relinqush
            (.yield Thread))

          (recur))))))
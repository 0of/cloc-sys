(ns cloc-scheduler.active-tasks
  (:require [rethinkdb.query :as r]
            [docker-client.support.rest :as rest]
            [docker-client.core :as docker]
            [cloc-scheduler.util :refer [db-run]] 
            [clojure.string :refer [split]]
            [cloc-scheduler.task-records :as records]))

(defonce ^:const table "active_tasks")

(defn- kill-docker!
  [client id]
  (rest/post client {} :kill-container {:container-id id} {}))

(defn- get-repo-location
  [parts]
  (format "https://github.com/%s/%s.git" (first parts) (second parts)))

(defn create-pending-task
  [{:keys [docker-client]} target task-id]
  (let [parts (split target #"/" 3)
        container-env [(format "USER_REPO=%s" (get-repo-location parts))
                       (format "REPO_BRANCH=%s" (last parts))]
        {container-id :id} (docker/create-container! docker-client {:env container-env
                                                                    :image "ubuntu:cloc-task"})]
    container-id))

(defn- target-exists?
  [db-conn target filter-state]
  (let [filter-state (name filter-state)
        task-count (db-run db-conn table (r/filter (r/fn [task]
                                                    (r/and 
                                                     (r/eq target (r/get-field task :target))
                                                     (r/eq filter-state (r/get-field task :state)))))
                                         r/count)]

    (> task-count 0)))

(defn try-start-task
  "tuple [status body]"
  [{:keys [db-conn docker-client] :as spec} target task-id]

  ;; ignore the one when the same target task is in pending state
  ;; which means stack up quite a lot same target tasks

  (if (target-exists? db-conn target :pending)
    ;; check same target is pending if so just return the pending state
    [:ok :pending]

    (try
      ;; pending the created task only if same target task is running
      ;; and it will block any future task initiations for the same target
      ;; otherwise let it run
      (let [will-it-start? (not (target-exists? db-conn target :running))
            container-id (create-pending-task spec target task-id)]
        (db-run db-conn table (r/insert {:id task-id
                                         :target target
                                         :container_id container-id 
                                         :state "pending"}))

        (when will-it-start?
          (docker/start-container! docker-client container-id)
          (db-run db-conn table (r/get task-id) (r/update {:state "running"})))

        [:ok (if will-it-start? :running :pending)])
      (catch Exception e
        [:error (.getMessage e)]))))

(defn try-start-pending-task
  [{:keys [db-conn docker-client] :as spec}]
  (try
    (let [sampled-task (first (db-run db-conn table (r/filter (r/fn [task] 
                                                               (r/eq "pending" (r/get-field task :state))
                                                               (r/sample 1)))))]
      (if (some? sampled-task)
        (do
          (docker/start-container! docker-client (:container_id sampled-task))
          (db-run db-conn table (r/get (:id sampled-task)) (r/update {:state "running"}))
          [:ok :running])
        [:error :not-found]))

    (catch Exception e
      [:error (.getMessage e)])))

(defn terminate-running-tasks
  [db-conn docker-client]
  (let [tasks (db-run db-conn table (r/filter (r/fn [task] (r/eq "running" (r/get-field task :state)))))]
    (doseq [{container-id :container_id id :id target :target} tasks]
      ;; if record exists which means container already removed
      (when-not (records/exists? db-conn id)
        (kill-docker! docker-client container-id) 
        (docker/remove-container! docker-client container-id)

        ;; add to record
        (records/add {:id id
                      :target target
                      :recorded_at (r/now)
                      :result "terminated"}))
    
      ;; remove from active tasks
      (db-run db-conn table (r/get id) (r/delete)))))

(defn try-cancel-task
  [{:keys [db-conn docker-client]} task-id]
  (let [{container-id :container_id id :id state :state :as task} (db-run db-conn table (r/get task-id))]
    (if (some? task)
      (try
        (docker/stop-container! docker-client container-id)
        (docker/remove-container! docker-client container-id)

         ;; add to record
        (records/add db-conn {:id id
                              :target (:target task)
                              :recorded_at (r/now)
                              :result "cancelled"})

        [:ok (keyword state)]

        (catch Exception e
          [:error (.getMessage e)]))

      [:error :not-found])))

(defn register-running-changes
  [db-conn]
  (-> (r/table table)
      (r/filter (r/fn [task]
                  (r/eq "finished" (r/get-field task :state))))
      (r/changes)
      (r/run db-conn {:async? true})))

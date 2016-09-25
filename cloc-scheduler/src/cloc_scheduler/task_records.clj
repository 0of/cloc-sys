(ns cloc-scheduler.task-records
  (:require [rethinkdb.query :as r]
            [cloc-scheduler.util :refer [db-run]]))

(defonce ^:const table "task_records") 

(defn exists?
  [db id]
  (> (db-run db table (r/get id) r/count)) 0)

(defn get-by-id
  [db id]
  (db-run db table (r/get id)))

(defn add
  [db body]
  (db-run db table (r/insert body)))
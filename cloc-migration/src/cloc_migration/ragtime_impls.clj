(ns cloc-migration.ragtime-impls
  (:require [rethinkdb.query :as r]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [ragtime.protocols :as p]
            [resauce.core :as resauce])
  (:import [java.io File])
  (:gen-class))

(defn- execute!
  [db-spec script]
  (with-open [conn (apply r/connect (flatten (vec db-spec)))]
    (binding [*ns* (the-ns *ns*)]
      (let [ns-sym (gensym "container")]
        (try
          (in-ns ns-sym)

          (clojure.core/refer-clojure)
          (clojure.core/require '[rethinkdb.query :as r])

          (intern *ns* 'execute-reql! (fn [terms] (r/run terms conn))) 
          (load-string script)

          (finally 
            (remove-ns ns-sym)))))))

(defn- execute-reql!
  [db-spec scripts]
  (doseq [s scripts]
    (execute! db-spec s)))

(defn- ensure-migrations-database-exists [db-conn migrations-db]
  (-> (r/db-list)
      (r/contains migrations-db)
      (r/do (r/fn [contains?]
              (r/branch contains? "" (r/db-create migrations-db))))
      (r/run db-conn)))

(defn- ensure-migrations-table-exists [db-conn migrations-db migrations-table]
  (ensure-migrations-database-exists db-conn migrations-db) 

  (-> (r/db migrations-db)
      (r/table-list)
      (r/contains migrations-table)
      (r/do (r/fn [contains?]
              (r/branch contains? "" (r/table-create (r/db migrations-db) migrations-table))))
      (r/run db-conn)))

(defn- insert-migration-record
  [db-conn migrations-db migrations-table id]
  (-> (r/db migrations-db)
      (r/table migrations-table)
      (r/insert {:id id
                 :created_at (r/now)})
      (r/run db-conn)))

(defn- delete-migration-record
  [db-conn migrations-db migrations-table id]
  (-> (r/db migrations-db)
      (r/table migrations-table)
      (r/get id)
      r/delete
      r/run db-conn))

(defn- query-migration-records
  [db-conn migrations-db migrations-table]
  (-> (r/db migrations-db)
      (r/table migrations-table)
      (r/pluck :id)
      (r/order-by "created_at")
      (r/run db-conn)
      ((partial map :id))))

(defrecord ReQLDatabase [db-spec migrations-db migrations-table]
  p/DataStore
  (add-migration-id [_ id]
    (with-open [conn (apply r/connect (flatten (vec (dissoc db-spec :db))))]
      (ensure-migrations-table-exists conn migrations-db migrations-table)
      (insert-migration-record conn migrations-db migrations-table id))) 
  
  (remove-migration-id [_ id]
    (with-open [conn (apply r/connect (flatten (vec (dissoc db-spec :db))))]
      (ensure-migrations-table-exists conn migrations-db migrations-table)
      (delete-migration-record conn migrations-db migrations-table id)))

  (applied-migration-ids [_]
    (with-open [conn (apply r/connect (flatten (vec (dissoc db-spec :db))))]
      (ensure-migrations-table-exists conn migrations-db migrations-table)
      (query-migration-records conn migrations-db migrations-table))))

(defrecord ReQLMigration [id up down]
  p/Migration
  (id [_] id)
  (run-up!   [_ db] (execute-reql! (:db-spec db) up))
  (run-down! [_ db] (execute-reql! (:db-spec db) down)))

(alter-meta! #'->ReQLMigration assoc :no-doc true)
(alter-meta! #'map->ReQLMigration assoc :no-doc true)

(defn reql-database
  "Given a db-spec and a map of options, return a Migratable database.
  The following options are allowed:
  :migrations-db - migrations database name
  :migrations-table - the name of the table to store the applied migrations
                      (defaults to ragtime_migrations)"
  ([db-spec]
   (reql-database db-spec {}))
  ([db-spec options]
   (->ReQLDatabase db-spec (:migrations-db options "ragtime_migrations") (:migrations-table options "ragtime_migrations"))))

(defn reql-migration
  [migration-map]
  (map->ReQLMigration migration-map))

(defn- file-extension [file]
  (re-find #"\.[^.]*$" (str file)))

(let [pattern (re-pattern (str "([^\\" File/separator "]*)\\" File/separator "?$"))]
  (defn- basename [file]
    (second (re-find pattern (str file)))))

(defmulti load-files
  (fn [files] (file-extension (first files))))

(defn- read-reql [file]
  (slurp file))

(defn- reql-file-parts [file]
  (rest (re-matches #"(.*?)\.(up|down)(?:\.(\d+))?\.reql" (str file))))

(defmethod load-files ".reql" [files]
  (for [[id files] (->> files
                        (group-by (comp first reql-file-parts))
                        (sort-by key))]
    (let [{:strs [up down]} (group-by (comp second reql-file-parts) files)]
      (reql-migration
       {:id   (basename id)
        :up   (map read-reql (sort-by str up))
        :down (map read-reql (sort-by str down))}))))

(defn- load-all-files [files]
  (->> (sort-by str files)
       (group-by file-extension)
       (vals)
       (mapcat load-files)))

(defn load-directory
  "Load a collection of Ragtime migrations from a directory."
  [path]
  (load-all-files (file-seq (io/file path))))

(defn load-resources
  "Load a collection of Ragtime migrations from a classpath prefix."
  [path]
  (load-all-files (resauce/resource-dir path)))
(ns cloc-migration.core
  (:require [cloc-migration.ragtime-impls :as reql]
            [ragtime.repl :as repl]
            [ragtime.strategy :as strategy])
  (:gen-class))  

(defn load-config []
  {:datastore (reql/reql-database {:host "127.0.0.1" :port 28015})
   :migrations (sort-by :id (reql/load-resources "migration"))
   :strategy strategy/apply-new})

(defn migrate []
 (try 
  (repl/migrate (load-config))
  (catch Exception e
   (prn e))))   

(defn rollback []
  (repl/rollback (load-config)))


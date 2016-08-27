(ns cloc-scheduler.util
  (:require [rethinkdb.query :as r]))

(defmacro db-run 
  [conn table & body]
  `(-> (r/table ~table)
       ~@body
       (r/run ~conn))) 
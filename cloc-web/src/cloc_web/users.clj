(ns cloc-web.users
  (:require [schema.core :as s]
            [rethinkdb.query :as r]
            [clojure.tools.logging :refer [info]]))

(defn register
  [{:keys [user repo]}]
  (let [id (format "github/%s/%s" user repo)]
    (with-open [conn (r/connect :host "127.0.0.1" :port 28015 :db "cloc")]
      (-> (r/table "users")
          (r/insert {:id id
                     :user user
                     :filter "*"
                     :lang "SUM"})
          (r/run conn)))))

(defn update-display-lang
  [{:keys [user repo]} body]
  (let [id (format "github/%s/%s" user repo)
        new-lang (if (> (count (:lang body)) 0)
                    (:lang body)
                    "SUM")]
    (with-open [conn (r/connect :host "127.0.0.1" :port 28015 :db "cloc")]
      (-> (r/table "users")
          (r/get id)
          (r/update {:lang new-lang})
          (r/run conn)))))

(ns cloc-web.users
  (:require [schema.core :as s]
            [rethinkdb.query :as r]
            [clojure.string :refer [split]]
            [clojure.tools.logging :refer [info]]))

(defn- get-repo-location
  [parts]
  (format "https://github.com/%s/%s.git" (first parts) (second parts)))

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

(defn list-registered-repos
  [{:keys [user]}]
  (with-open [conn (r/connect :host "127.0.0.1" :port 28015 :db "cloc")]
    (let [repos (-> (r/table "users") 
                    (r/filter (r/fn [u] (r/eq user (r/get-field u :user))))
                    (r/run conn))
          to-repo-url (fn [id] 
                        (let [parts (rest (split id #"/" 3))]
                          (get-repo-location parts)))]
      (map #(update-in % [:id] to-repo-url)))))


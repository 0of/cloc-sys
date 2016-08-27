(ns cloc-reporter.submit
  (:require [schema.core :as s]
            [rethinkdb.query :as r]
            [clojure.tools.logging :refer [info]]))

(s/defschema LangStats {:lang s/Str
                        :total s/Int
                        :code s/Int})

(s/defschema CountResult {:user s/Str
                          :repo s/Str
                          :branch s/Str
                          :sum {:total s/Int
                                :code s/Int}
                          :langs [LangStats]})

(s/defschema Failure {:reason s/Str})

(defn- update-task-state
  [conn task-id state]
  (-> (r/table "active_tasks")
      (r/get task-id)
      (r/update (r/fn [task]
                  {:state
                    (r/branch (r/eq "running" (r/get-field task :state))
                      state
                      (r/get-field task :state))}))
      (r/run conn)))

(defn- save
  [task-id {:keys [user repo branch sum langs]}]
  (let [id (format "%s/%s/%s" user repo branch)
        ;; remove duplicated items
        langs (->> (group-by :lang langs)
                   (map (fn [[k v]] {k (if (sequential? v) (first v) v)}))
                   (into {}))]
    ;; save to db
    (with-open [conn (r/connect :host "127.0.0.1" :port 28015 :db "cloc")]
     (-> (r/table "result")
         (r/insert (merge {:id id} langs sum)
                   {:conflict :replace :return-changes true :durability :hard})
         (r/run conn))

     (update-task-state conn task-id "finished"))))

(defn submit
  [req]
  (let [task-id (:task-id (:params req))
        body (:body req)]
    (s/validate s/Str task-id)
    (s/validate CountResult body)

    (let [{error_count :errors error :first_error} (save task-id body)]
      (if (> error_count 0)
        {:status 400 :body error}
        ""))))

(defn fail
  [req]
  (let [task-id (:task-id (:params req))
        body (:body req)]

    (s/validate s/Str task-id)
    (s/validate Failure body)
    
    (with-open [conn (r/connect :host "127.0.0.1" :port 28015 :db "cloc")]
    ;; TODO
      (update-task-state conn task-id "finished"))))

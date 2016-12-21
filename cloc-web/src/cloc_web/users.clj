(ns cloc-web.users
  (:require [schema.core :as s]
            [rethinkdb.query :as r]
            [clojure.string :refer [split]]
            [clj-http.client :as client]
            [cheshire.core :refer [parse-string]]
            [clojure.tools.logging :refer [info]]))

(defn- get-repo-location
  ([first-part second-part]
   (format "https://github.com/%s/%s.git" first-part second-part))
  ([full-name]
   (format "https://github.com/%s.git" full-name)))

(defn- get-user-repos
  [user]
  (format "https://api.github.com/users/%s/repos" user))

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

(defn unregister
  [{:keys [user repo]}]
  (let [id (format "github/%s/%s" user repo)]
    (with-open [conn (r/connect :host "127.0.0.1" :port 28015 :db "cloc")]
      (-> (r/table "users")
          (r/get id)
          r/delete
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
          to-repo-url (fn [repo] 
                        (let [[first-part second-part] (rest (split (:id repo) #"/" 3))]
                          (get-repo-location first-part second-part)))]
      (map (juxt to-repo-url identity) repos))))

(defn- list-github-repos
  [user]
  (let [repos (some-> (client/get (get-user-repos user) {:accept :json})
                      :body
                      (parse-string true))
        extract-values #(select-keys % [:name :full_name :description])]
    (map (juxt :git_url extract-values) repos)))

(defn list-repos
  [{:keys [user] :as params}]
  (let [repos (into {} (list-github-repos user)) ;; {"url":object}
        registered (into {} (list-registered-repos params))
        add-registered (fn [repo registered]
                          (merge repo {:registered registered}))
        into-vec (fn [[url values]]
                    (assoc values :url url))]
    (map into-vec (merge-with add-registered repos registered))))

(defn me
  [{:keys [user] :as params}]
  {:status 200
   :body {:user user
          :login "github"
          :repos [{:url (get-repo-location "0of/rep")
                   :name "rep"
                   :full_name "0of/rep" 
                   :description "description"}
                  {:url (get-repo-location "0of/rep2")
                   :name "rep2"
                   :full_name "0of/rep2" 
                   :description "description"
                   :registered {:id "0of/rep2"
                                :user "0of"
                                :filter "*"
                                :lang "SUM"}}]}})

(defn is_login
  [{:keys [user] :as params}]
  {:status 200 
   :body {:result (some? user) 
          :user user}})

(defn get-repo
  [{:keys [user repo] :as params}]
  (let [id (format "github/%s/%s" user repo)]
    (with-open [conn (r/connect :host "127.0.0.1" :port 28015 :db "cloc")]
      (if-let [repo-config (-> (r/table "users")
                               (r/get id)
                               (r/run conn))]
        {:status 200 :body {:config repo-config 
                            :result (-> (r/table "result")
                                        (r/get id)
                                        (r/run conn))}}
        {:status 404}))))
(ns cloc-webhook.core
  (:require [ring.adapter.jetty :as jetty]
            [compojure.core :refer :all]
            [ring.middleware.params :as params]
            [clojure.string :refer [split]]
            [taoensso.carmine :as car :refer [wcar]]
            [byte-streams :refer [to-byte-array]]
            [cheshire.core :refer [parse-string]])
  (:import [javax.crypto Mac]
           [javax.crypto.spec SecretKeySpec]
           [org.apache.commons.codec.binary Hex])
  (:gen-class))

(def ^:const secret "cloc-webhook")

(def spec {:redis-spec {:pool {}
                        :spec {:host "127.0.0.1"
                               :port 6379}}})

(defn hmac
  [data]
  (let [algo "HmacSHA1"
        signing-key (SecretKeySpec. (.getBytes secret) algo)
        mac (doto (Mac/getInstance algo) (.init signing-key))]
    (str "sha1="
         (Hex/encodeHexString (.doFinal mac data)))))

(defn on-commit-pushed
  [{redis-spec :redis-spec} req]
  (let [event-type (get-in req [:headers "x-github-event"])
        sig (get-in req [:headers "x-hub-signature"])
        body (:body req)
        bytes (to-byte-array body)]
    (if (and (= event-type "push")
             (= sig (hmac bytes)))
      (let [body (-> bytes
                     slurp
                     (parse-string true))
            ref (:ref body)
            branch (or (last (split ref #"/" 3))
                       (get-in body [:repository :default_branch])
                       "master")
            target (format "%s/%s" (get-in body [:repository :full_name]) branch)
            new-id (car/wcar redis-spec (car/incr "job_ids"))]

        ;; queue task
        (car/wcar redis-spec (car/zadd "scheduling" new-id target))
        ;; notify
        (car/wcar redis-spec (car/publish "task-queued" ""))

        {:status 200 :body ""})

      {:status 400 :body "invalid event"})))

(defroutes app
  (POST "/repo/hooks" req (on-commit-pushed spec req)))  

(defn -main
  [& args]
  (jetty/run-jetty app {:port 50001}))

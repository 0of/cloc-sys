(ns cloc-web.core-test
  (:require [clojure.test :refer :all]
            [cloc-web.core :refer :all]
            [ring.adapter.jetty :as jetty]
            [rethinkdb.query :as r]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [cloc-web.auth :refer [jwt-token]]
            [clj-time.core :refer [days]]
            [clojure.xml :as xml])
  (:import [java.io Closeable]))

(defonce ^:const port 50002)
(defonce ^:const url "http://localhost:50002")

(def db-conn (atom nil))

(defrecord Server [server]
  Closeable
  (close [this] (.stop server)))

(defn setup-server
 [f]
 (with-open [_ (->Server (jetty/run-jetty #'handlers {:port port :join? false}))
             conn (r/connect :host "127.0.0.1" :port 28015 :db "cloc")]
   (reset! db-conn conn)
   (f)))

(defn setup-each
  [f]
  (f)
  (-> (r/table "users")
      r/delete
      (r/run @db-conn))
  (-> (r/table "result")
      r/delete     
      (r/run @db-conn)))

(defmacro scenario [aside & body] `(do ~@body))

(use-fixtures :once setup-server)
(use-fixtures :each setup-each)

(defn- get-display-lang
  [id]
  (-> (r/table "users")
      (r/get id)
      (r/run @db-conn)
      :lang))

(deftest register-with-unauthorized-user
  (testing "no session"
    (is (= 401 (:status (client/post (str url "/user/repo/register") {:accept :json
                                                                      :throw-exceptions false})))))

  (testing "invalid session id"
    (with-redefs [clj-http.client/get (fn [& param] {:status 400
                                                     :body {:error ""}})]
      (is (= 401 (:status (client/post (str url "/user/repo/register") {:accept :json
                                                                        :throw-exceptions false
                                                                        :headers {"Authorization" (format "Bearer %s" (jwt-token "token" {:expires (-> 28 days)}))}})))))))

(deftest register-with-authorized-user
  (with-redefs [clj-http.client/get (fn [& param] {:status 200
                                                   :body {:login "0of"}})]
    (is (= 200 (:status (client/post (str url "/user/repo/register") {:accept :json
                                                                      :throw-exceptions false
                                                                      :headers {"Authorization" (format "Bearer %s" (jwt-token "token" {:expires (-> 28 days)}))}}))))))

(deftest patch-display-lang-with-unauthorized-user
 (testing "no session"
    (is (= 401 (:status (client/patch (str url "/user/target/display_lang") {:accept :json
                                                                             :body ""
                                                                             :content-type :json
                                                                             :throw-exceptions false}))))

  (testing "invalid session id"
    (let [client-get clj-http.client/get]
      (with-redefs [clj-http.client/get (fn [& param] {:status 400
                                                       :body {:error ""}})]
        (is (= 401 (:status (client/patch (str url "/user/target/display_lang") {:accept :json
                                                                                 :body ""
                                                                                 :content-type :json
                                                                                 :throw-exceptions false
                                                                                 :headers {"Authorization" (format "Bearer %s" (jwt-token "token" {:expires (-> 28 days)}))}})))))))))

(deftest patch-display-lang-with-authorized-user
  (scenario "register repos"
    (-> (r/table "users")
        (r/insert {:id "github/0of/target"
                   :user "0of"
                   :filter "*"
                   :lang "SUM"})
        (r/run @db-conn)))

  (with-redefs [clj-http.client/get (fn [& param] {:status 200
                                                   :body {:login "0of"}})]

    (testing "Clojure as display language"
      (-> (str url "/user/target/display_lang")
          (client/patch {:accept :json
                         :body  (cheshire/generate-string {:lang "Clojure"})
                         :content-type :json
                         :as :json
                         :throw-exceptions false
                         :headers {"Authorization" (format "Bearer %s" (jwt-token "token" {:expires (-> 28 days)}))}})
          :status
          (= 200)
          (and (= "Clojure" (get-display-lang "github/0of/target")))
          is)

     (testing "Default display language"
        (-> (str url "/user/target/display_lang")
            (client/patch {:accept :json
                           :body ""
                           :content-type :json
                           :as :json
                           :throw-exceptions false
                           :headers {"Authorization" (format "Bearer %s" (jwt-token "token" {:expires (-> 28 days)}))}})
            :status
            (= 200)
            (and (= "SUM" (get-display-lang "github/0of/target")))
            is)))))

(deftest get-svg-non-existent-user
  (is (= 404 (:status (client/get (str url "/0of/target/master/svg_badge") {:as :stream 
                                                                            :throw-exceptions false})))))

(deftest get-svg
  (scenario "register repo and insert record"
    (-> (r/table "users")
        (r/insert {:id "github/0of/target"
                   :user "0of"
                   :filter "*"
                   :lang "SUM"})
        (r/run @db-conn))

    (-> (r/table "result")
        (r/insert {"Clojure" {"code" 1891
                              "lang" "Clojure"
                              "total" 2394}
                   "code" 1891
                   "id" "0of/target/master"
                   "total" 2394})
        (r/run @db-conn)))

  (let [svg-doc (xml/parse (:body (client/get (str url "/0of/target/master/svg_badge") {:as :stream})))]
    (is (= "2394" (get-in svg-doc [:content 1 :content 1 :content 0])))))

(deftest get-svg-after-filter-updated
  (scenario "register repo and insert record"
    (-> (r/table "users")
        (r/insert {:id "github/0of/target"
                   :user "0of"
                   :filter "*"
                   :lang "SUM"})
        (r/run @db-conn))
    (-> (r/table "result")
        (r/insert {"Clojure" {"code" 1891
                              "lang" "Clojure"
                              "total" 2394}
                   "C++" {"code" 341
                          "lang" "C++"
                          "total" 520}
                   "code" 2232
                   "id" "0of/target/master"
                   "total" 2914})
        (r/run @db-conn)))


  (with-redefs [clj-http.client/get (fn [& param] {:status 200
                                                   :body {:login "0of"}})]

    (testing "Clojure as display language"
      (-> (str url "/user/target/display_lang")
          (client/patch {:accept :json
                         :body  (cheshire/generate-string {:lang "Clojure"})
                         :content-type :json
                         :as :json
                         :throw-exceptions false
                         :headers {"Authorization" (format "Bearer %s" (jwt-token "token" {:expires (-> 28 days)}))}})
          :status
          (= 200)
          (and (= "Clojure" (get-display-lang "github/0of/target")))
          is)))

  (testing "total count"
    (let [svg-doc (xml/parse (:body (client/get (str url "/0of/target/master/svg_badge") {:as :stream})))]
      (is (= "2394" (get-in svg-doc [:content 1 :content 1 :content 0]))))))

(deftest get-png-non-existent-user
  (is (= 404 (:status (client/get (str url "/0of/target/master/png_badge") {:as :stream 
                                                                            :throw-exceptions false})))))

(deftest get-png
  (scenario "register repo and insert record"
    (-> (r/table "users")
        (r/insert {:id "github/0of/target"
                   :user "0of"
                   :filter "*"
                   :lang "SUM"})
        (r/run @db-conn))

    (-> (r/table "result")
        (r/insert {"Clojure" {"code" 1891
                              "lang" "Clojure"
                              "total" 2394}
                   "code" 1891
                   "id" "0of/target/master"
                   "total" 2394})
        (r/run @db-conn)))

  (let [header (:headers (client/get (str url "/0of/target/master/png_badge")))]
    (is (= (get header "Content-Type") "image/png"))))

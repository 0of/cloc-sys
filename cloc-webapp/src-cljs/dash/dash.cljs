(ns cloc-webapp.dash
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [goog.events :as events]
            [cljs.core.async :refer [put! <! >! chan timeout]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs-http.client :as http]))

(def app-state (atom {}))

;; left panel to list all the repos
(defn repo-list-view [state owner]
  (reify
    om/IDidMount
    (did-mount [this]    
      (go (let [resp (<! (http/get "http://localhost:4679/users/repos" {:oauth-token (om/get-state this :auth)}))  
                repos (:body resp)]
            (when repos
              (om/set-state! this :repos repo)))))      
   
    om/IRenderState
    (render-state [this state]  
      (if (:repos state)
        (apply dom/ul nil
          (map (fn [repo] (dom/li nil (:full_name repo))) (:repos this)))))))


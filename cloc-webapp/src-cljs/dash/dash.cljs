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
      (go (let [resp (<! (http/get "/api/user/me" {:oauth-token (om/get-state this :auth)}))]  
            (if (= 200 (:status resp))
               (om/set-state! this :me (:body resp))
               (om/set-state! this :repos repo)))))      
   
    om/IRenderState
    (render-state [this state]  
      (if (:repos state)
        (apply dom/ul nil
          (map (fn [repo] (dom/li nil (:full_name repo))) (:repos this)))))))




(om/root widget app-state {:target (.getElementById js/document "content")})

(ns cloc-webapp.core
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [cljs.core.async :refer [put! <! >! chan timeout]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs-http.client :as http])
  (:import [goog.net Cookies]))

(enable-console-print!)

(def app-state (atom {:user nil}))

(defn widget [state owner]
  (reify  
    om/IInitState
    (init-state [_]
      {:auth (.get (Cookies.) "session_value")})

    om/IRenderState
    (render-state [this state]  
      (if (:user state)
        (dom/a #js {:ref "/dash"} "Dashboard")
        (dom/a #js {:ref "/login"} "Login")))

    om/IDidMount
    (did-mount [this]    
      (go (let [resp (<! (http/get "http://localhost:4679/users/me" {:oauth-token (om/get-state this :auth)}))  
                user (get-in resp [:body :user])]
            (prn resp)            
            (when user
              (om/set-state! this :user user)))))))      

(om/root widget app-state {:target (.getElementById js/document "content")})
(ns cloc-webapp.core
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [cljs.core.async :refer [put! <! >! chan timeout]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs-http.client :as http]
            [goog.window :refer [open]])
  (:import [goog.net Cookies]))

(enable-console-print!)

(def app-state (atom {:user nil}))

(defn widget [state owner]
  (reify  
    om/IInitState
    (init-state [_]
      {:auth (.get (Cookies. js/document) "session_id")})

    om/IRenderState
    (render-state [this state]
      (dom/nav #js {:className "navbar navbar-default navbar-static-top"}
        (dom/div #js {:className "container-fluid"}
          (dom/a #js {:className "navbar-brand" :href "#"} "CLOC")

          (dom/ul #js {:className "nav navbar-nav navbar-right"}
            (if (:user state)
              (dom/a #js {:href "/dash" :className "navbar-link"} "Dashboard")
              (dom/a #js {:href "https://github.com/login/oauth/authorize?client_id=" :className "navbar-link"} "Github Login"))))))

    om/IDidMount
    (did-mount [this]
      (go (let [resp (<! (http/get "/api/user/is_login" {:oauth-token (om/get-state owner :auth)}))
                user (get-in resp [:body :user])]
            (prn resp)
            (when user
              (om/set-state! owner :user user)))))))

(om/root widget app-state {:target (.getElementById js/document "content")})
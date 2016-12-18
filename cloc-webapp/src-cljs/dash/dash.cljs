(ns cloc-webapp.dash
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [goog.json :as json]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs-http.client :as http])
  (:import [goog.net Cookies]))  

(enable-console-print!)

(def app-state (atom {}))

(defn repo-list-view [state owner]
  (reify
    om/IRender
    (render [this]
      (apply dom/ul nil
        (map (fn [repo] (dom/li nil (get repo "full_name"))) state)))))

(defn widget [state owner]
  (reify  
    om/IInitState
    (init-state [_]
      {:me (js->clj (json/parse (.-cloc_me js/window)))
       :auth (.get (Cookies. js/document) "session_id")})

    om/IRenderState
    (render-state [this state]
      (dom/div nil
        (om/build repo-list-view (get-in state [:me "repos"]))))))

(om/root widget app-state {:target (.getElementById js/document "content")})

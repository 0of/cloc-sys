(ns cloc-webapp.dash
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [goog.json :as json]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs-http.client :as http])
  (:import [goog.net Cookies]))  

(def app-state (atom {}))

(defn repo-list-view [state owner]
  (reify
    om/IRenderState
    (render-state [this state]  
      (apply dom/ul nil
        (map (fn [repo] (dom/li nil (:full_name repo))) state)))))

(defn widget [state owner]
  (reify  
    om/IInitState
    (init-state [_]
      {:me (json/parse (.-cloc_me js/window))
       :auth (.get (Cookies. js/document) "session_id")})

    om/IRenderState
    (render-state [this state]
      (build repo-list-view {:state (:repo state)}))))

(om/root widget app-state {:target (.getElementById js/document "content")})

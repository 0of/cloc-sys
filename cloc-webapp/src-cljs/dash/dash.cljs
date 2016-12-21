(ns cloc-webapp.dash
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [goog.json :as json]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs-http.client :as http])
  (:import [goog.net Cookies]))  

(enable-console-print!)

(def app-state (atom {:current-index [0]}))

(defn current-index-cur []
  (om/ref-cursor (:current-index (om/root-cursor app-state))))

(defn- repo-button [index repo]
  (dom/button 
    #js {:onClick
           (fn [e] (om/update! (current-index-cur) [0] index))}
    (get repo "full_name")))

(defn repo-list-view [state owner]
  (reify
    om/IRender
    (render [this]
      (apply dom/div nil
        (map-indexed repo-button state)))))           

(defn repo-detail-view [state owner]
  (reify
    om/IInitState
    (init-state [_]
      state)

    om/IRenderState
    (render-state [this state]  
      (let [current-index (nth (om/observe owner (current-index-cur)) 0)]
        (if (< current-index (count state))
          (let [current-repo (nth state current-index)] 
            ;; check registered or not   
            (dom/h2 nil (get current-repo "full_name")))
          (dom/h2 nil "Add your repos"))))))

(defn widget [state owner]
  (reify  
    om/IInitState
    (init-state [_]
      {:me (js->clj (json/parse (.-cloc_me js/window)))
       :auth (.get (Cookies. js/document) "session_id")})

    om/IRenderState
    (render-state [this state]
      (let [repos (vec (get-in state [:me "repos"]))]  
        (dom/div nil
          (om/build repo-list-view repos)
          (om/build repo-detail-view repos))))))

(om/root widget app-state {:target (.getElementById js/document "content")})

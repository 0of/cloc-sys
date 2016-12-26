(ns cloc-webapp.dash
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [goog.json :as json]
            [goog.string :as str] 
            [goog.string.format] 
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

;; {:auth - :repo -}
(defn repo-config-panel [state owner]
  (reify
    om/IInitState
    (init-state [_]
      (let [mutable (atom {:state (if 
                                    (:registered current-repo) 
                                    [:registered]
                                    [:unregistered])})]
        (merge state {:mutable mutable
                      :state-cursor (:state (om/root-cursor mutable))})))   

    om/IRenderState
    (render-state [this state]
      (let [cursor (om/ref-cursor (:state-cursor state))   
            current-state (nth (om/observe owner cursor) 0)
            auth-token (:auth state)
            register-repo-url (str/format "/api/user/%s/register" (get-in state [:repo "name"]))
            register-fn (fn [] 
                          (when (= current-state :unregistered)
                            (om/update! cursor [0] :registering)  

                            (go (let [resp (<! (http/post register-repo-url {:oauth-token auth-token
                                                                             :json-params {}}))]
                                   (if (= 200 (:status resp))
                                     (prn resp)  
                                     (om/update! cursor [0] :unregistered))))))]

        (case current-state
          :registered (dom/h2 nil (get (:repo state) "full_name"))
          :registering (dom/h2 
                          nil
                          "registering...")
          :unregistered (dom/button 
                          #js {:onClick register-fn}
                          "register this repo"))))))

;; {:repos - :auth -}
(defn repo-detail-view [state owner]
  (reify
    om/IInitState
    (init-state [_]
      state)

    om/IRenderState
    (render-state [this state]  
      (let [current-index (nth (om/observe owner (current-index-cur)) 0)
            repos (:repos state)]    
        (if (< current-index (count repos))
          (om/build repo-config-panel {:repo (nth repos current-index) 
                                       :auth (:auth state)})                              
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
        (prn repos)
        (dom/div nil
          (om/build repo-list-view repos)
          (om/build repo-detail-view {:repos repos
                                      :auth (:auth state)}))))))                                       

(om/root widget app-state {:target (.getElementById js/document "content")})

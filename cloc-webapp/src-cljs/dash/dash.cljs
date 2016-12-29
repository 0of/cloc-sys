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

(def app-state (atom {:current-index [0]
                      :repos []
                      :registered-states []}))             

;; cursors
(defn current-index-cur []
  (om/ref-cursor (:current-index (om/root-cursor app-state))))

(defn registered-state-cur [index]
  (om/ref-cursor (get (om/root-cursor app-state) [:registered-states index])))  

;; views
(defn repo-list-view [state owner]
  (reify
    om/IRender
    (render [this]
      (letfn [(repo-button [index repo]
                (dom/button 
                  #js {:onClick
                         (fn [e] (om/update! (current-index-cur) [0] index))}
                  (get repo "full_name")))]
        (apply dom/div nil
          (map-indexed repo-button (:repos @app-state)))))))  

;; {:auth - :repo -}
(defn repo-config-panel [state owner]
  (reify
    om/IRender
    (render [this]
      (let [index (:index state)
            current-state (nth (om/observe owner (registered-state-cur index)) 0)
            auth-token (:auth state)
            register-repo-url (str/format "/api/user/%s/register" (get-in state [:repo "name"]))
            register-fn (fn [] 
                          (when (= current-state :unregistered)
                            (om/update! (registered-state-cur index) [0] :registering)  

                            (go (let [resp (<! (http/post register-repo-url {:oauth-token auth-token
                                                                             :json-params {}}))]
                                   (if (= 200 (:status resp))
                                     (prn resp)
                                     (om/update! (registered-state-cur index) [0] :unregistered))))))]      

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
    om/IWillMount
    (will-mount [_]
      ;; check all registered state
      (let [registered (mapv #(if
                                (:registered %) 
                                [:registered]
                                [:unregistered])
                          (:repos @app-state))]
        (swap! app-state (fn [state] (assoc state :registered-states :registered)))))

    om/IRender
    (render [this]
      (let [current-index (nth (om/observe owner (current-index-cur)) 0)
            repos (:repos @app-state)]
        (if (< current-index (count repos))
          (om/build repo-config-panel {:repo (nth repos current-index) 
                                       :auth (:auth state)})                              
          (dom/h2 nil "Add your repos"))))))

(defn widget [state owner]
  (reify  
    om/IInitState
    (init-state [_]
      {:auth (.get (Cookies. js/document) "session_id")})

    om/IWillMount
    (will-mount [_]
      (let [me (js->clj (json/parse (.-cloc_me js/window)))
            repos (or (vec (get me "repos")) [])]
        (swap! app-state (fn [state] (assoc state :repos repos)))))

    om/IRenderState
    (render-state [this state]  
      (dom/div nil
        (om/build repo-list-view nil)
        (om/build repo-detail-view {:auth (:auth state)})))))

(om/root widget app-state {:target (.getElementById js/document "content")})

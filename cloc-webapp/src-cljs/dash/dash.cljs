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
    om/IInitState
    (init-state [_]
      (let [mutable (atom {:state (if 
                                    (:registered current-repo) 
                                    [:registered]
                                    [:unregistered])})]
        (merge state {:mutable mutable
                      :state-cursor (:state (om/root-cursor mutable))})))   

    om/IWillMount
    (will-mount [_]
      (prn "repo panel mount"))

    om/IRenderState
    (render-state [this state]
      (prn "repo panel render")

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
      (let [repo-ref (om/ref-cursor (:repos (om/root-cursor app-state)))]
        (dom/div nil
          (om/build repo-list-view nil)
          (om/build repo-detail-view {:repos-ref repo-ref
                                      :index-ref (om/ref-cursor (:current-index (om/root-cursor app-state)))                      
                                      :auth (:auth state)}))))))

(om/root widget app-state {:target (.getElementById js/document "content")})

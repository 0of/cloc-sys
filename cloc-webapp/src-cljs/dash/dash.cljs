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
  (om/ref-cursor (get-in (om/root-cursor app-state) [:registered-states index])))  

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

(defn- register-repo [current-index auth-token repo-name]
  (let [register-repo-url (str/format "/api/user/%s/register" repo-name)]  
    (om/update! (registered-state-cur current-index) [0] :registering)
    (go (let [resp (<! (http/post register-repo-url {:oauth-token auth-token
                                                     :json-params {}}))]
           (if (= 200 (:status resp))
             (let [registered-repo (:body resp)]
               (swap! app-state (fn [state] (assoc-in state [:repos current-index :registered] registered-repo)))
               (om/update! (registered-state-cur current-index) [0] :registered))  

             (om/update! (registered-state-cur current-index) [0] :unregistered))))))

(defn- unregister-repo [current-index auth-token repo-name]
  (let [unregister-repo-url (str/format "/api/user/%s/unregister" repo-name)]  
    (go (let [resp (<! (http/post unregister-repo-url {:oauth-token auth-token
                                                       :json-params {}}))]
           (when (= 200 (:status resp))
              (swap! app-state (fn [state] (update-in state [:repos current-index] dissoc :registered)))
              (om/update! (registered-state-cur current-index) [0] :unregistered))))))  

(defn- save-repo [current-index auth-token repo-name body]
  "save display language"
  (let [save-repo-url (str/format "/api/user/%s/display_lang" repo-name)]  
    (go (let [resp (<! (http/patch save-repo-url {:oauth-token auth-token
                                                  :json-params {:display_lang body}}))]
           (when (= 200 (:status resp))
              (swap! app-state (fn [state]
                                 (if-let [registered (get-in [:repos current-index :registered] state)]
                                   (assoc-in state [:repos current-index :registered] (assoc registered :lang body))
                                   state))))))))                      

(defn repo-badge-panel [state owner]
  (reify  
    om/IRender
    (render [this]
      (let [svg-path (get state "svg_badge_url")]  
        (dom/div nil
          (dom/img #js{:src svg-path})  
          (dom/select
            #js {:onChange (fn [ev]
                             (let [this (.-target ev)
                                   opt-value (aget (.-options this) (.-selectedIndex this) "value")]
                                (aset (om/get-node owner "badge-link") "value" opt-value)))}      
            (dom/option #js {:value svg-path} "SVG badge")
            (dom/option #js {:value (get state "png_badge_url")} "PNG badge"))    
          (dom/textarea #js {:value svg-path :ref "badge-link"}))))))        

;; {:auth - :repo -}
(defn repo-config-panel [state owner]
  (reify
    om/IRender
    (render [this]
      (let [index (:index state)
            current-state (nth (om/observe owner (registered-state-cur index)) 0)
            current-repo (nth (:repos @app-state) index)]

        (case current-state
          :registered (dom/div nil
                        (dom/h2 nil (get current-repo "full_name"))
                        
                        (if-let [result (get-in current-repo ["registered" "result"])]
                          (om/build repo-badge-panel result)
                          nil)

                        (dom/input #js {:type "text" :ref "display-lang" :value (:lang current-repo)})
                        (dom/button
                          #js {:onClick #(when (= current-state :registered)
                                            (unregister-repo index (:auth state) (get current-repo "name")))}             
                          "unregister this repo")
                        (dom/button
                          #js {:onClick #(save-repo index (:auth state) (get current-repo "name") (.-value (om/get-node owner "display-lang")))}
                          "Save"))
          :registering (dom/h2 
                          nil
                          "registering...")
          :unregistered (dom/button 
                          #js {:onClick #(when (= current-state :unregistered)
                                            (register-repo index (:auth state) (get current-repo "name")))}             
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
        (swap! app-state (fn [state] (assoc state :registered-states registered)))))

    om/IRender
    (render [this]
      (let [current-index (nth (om/observe owner (current-index-cur)) 0)
            repos (:repos @app-state)]
        (if (< current-index (count repos))
          (om/build repo-config-panel {:index current-index
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

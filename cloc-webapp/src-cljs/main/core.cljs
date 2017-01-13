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
      (dom/div nil
        (dom/nav #js {:className "navbar navbar-default navbar-static-top"}
          (dom/div #js {:className "container-fluid"}
            (dom/a #js {:className "navbar-brand" :href "#"} "CLOC")

            (dom/ul #js {:className "nav navbar-nav navbar-right"}
              (dom/li nil
                (if (:user state)
                  (dom/a #js {:href "/dash" :className "navbar-link"} "Dashboard")
                  (dom/a #js {:href "https://github.com/login/oauth/authorize?client_id=" :className "navbar-link"} "Github Login"))))))
        
        (dom/div #js {:className "jumbotron"}
          (dom/div #js {:className "container"}
            (dom/h1 nil "CLOC System")
            (dom/p nil "count lines of code system")))

        (dom/footer #js {:className "footer"}
          (dom/div #js {:className "container"}
            (dom/p nil "Designed and built with all the ❤️ by "
              (dom/a #js {:href "https://github.com/0of"} "@magnus"))
            (dom/ul #js {:className "list-inline"}
              (dom/li nil
                (dom/a #js {:href "https://github.com/0of/cloc-sys"} "Github"))
              (dom/li nil
                (dom/a #js {:href "#"} "About"))
              (dom/li nil
                (dom/a #js {:className "github-button" :href "https://github.com/0of/cloc-sys"
                            :data-count-href "/0of/cloc-sys/stargazers" :data-count-api "/repos/0of/cloc-sys#stargazers_count"
                            :data-count-aria-label "# stargazers on GitHub" :aria-label "Star 0of/cloc-sys on GitHub"}
                  "Star")))))))

    om/IDidMount
    (did-mount [this]
      (go (let [resp (<! (http/get "/api/user/is_login" {:oauth-token (om/get-state owner :auth)}))
                user (get-in resp [:body :user])]
            (prn resp)
            (when user
              (om/set-state! owner :user user)))))))

(om/root widget app-state {:target (.-body js/document)})
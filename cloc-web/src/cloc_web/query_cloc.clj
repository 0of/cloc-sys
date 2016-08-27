(ns cloc-web.query-cloc
  (:require [schema.core :as s]
            [rethinkdb.query :as r]
            [selmer.parser :refer [render-file]]
            [clojure.tools.logging :refer [info]]
            [selmer.util :as u])

 (:import [java.awt.geom.Rectangle2D]
          [java.awt.font FontRenderContext]
          [java.awt Font RenderingHints]))

(defn- get-langs
  [id]
  (with-open [conn (r/connect :host "127.0.0.1" :port 28015 :db "cloc")]
    (-> (r/table "result")
        (r/get id)
        (r/run conn))))

(defn- measure-text-width
  [text]
  (let [f (Font. "Verdana" Font/PLAIN 11)
        render-context (FontRenderContext. nil RenderingHints/VALUE_TEXT_ANTIALIAS_DEFAULT RenderingHints/VALUE_FRACTIONALMETRICS_DEFAULT)] 
    (.. f (getStringBounds text render-context) (getWidth))))

(defn- format-total
  [lang-total]
  (cond
    (< lang-total 10000)
    (str lang-total)

    (< lang-total (long 1e8))
    (format "%dk" (/ lang-total 1000))

    :larger
    (format "%dm" (/ lang-total (long 1e6)))))

(defn- build-context-map
  [langs]
  (let [content ["code of lines:"  (-> (get-in langs [:total])
                                       format-total)] 
        widths (vec (map measure-text-width content)) 
        offset [(/ (first widths) 2) 
                (+ (first widths) (- (/ (second widths) 2) 1))]]
    {:text content
     :widths widths
     :offset offset
     :badge_width (reduce + widths) 
     :colors ["#555" "#4c1"]}))

(defn get-svg-badge
  [{:keys [user repo branch]}]
  (let [query-id (format "%s/%s/%s" user repo branch)
        langs (get-langs query-id)]
    (render-file "templates/flat-badge.svg" (build-context-map langs))))


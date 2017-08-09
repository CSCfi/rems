(ns rems.reviewals
  (:require [compojure.core :refer [GET defroutes]]
            [rems.layout :as layout]))

(defn reviewals-page []
  (layout/render
    "reviewals"
    [:p "hello world"]))

(defroutes reviewals-routes
  (GET "/reviewals" [] (reviewals-page)))

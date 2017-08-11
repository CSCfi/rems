(ns rems.reviews
  (:require [compojure.core :refer [GET defroutes]]
            [rems.layout :as layout]))

(defn reviews-page []
  (layout/render
    "reviews"
    [:p "hello world"]))

(defroutes reviews-routes
  (GET "/reviews" [] (reviews-page)))

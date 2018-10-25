(ns rems.atoms
  (:require [komponentit.autosize :as autosize]))

(defn external-link []
  [:i {:class "fa fa-external-link-alt"}])

(defn link-to [opts uri title]
  [:a (merge opts {:href uri}) title])

(defn image [opts src]
  [:img (merge opts {:src src})])

(defn sort-symbol [sort-order]
  [:i.fa {:class (case sort-order
                   :asc "fa-arrow-up"
                   :desc "fa-arrow-down")}])

(defn search-symbol []
  [:i.fa {:class "fa-search"}])

(defn textarea [attrs]
  [autosize/textarea (merge {:class "form-control" :min-rows 5} attrs)])
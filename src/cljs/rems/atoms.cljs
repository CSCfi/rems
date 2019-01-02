(ns rems.atoms
  (:require [komponentit.autosize :as autosize]
            [rems.guide-functions])
  (:require-macros [rems.guide-macros :refer [component-info example]]))

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

(defn flash-message
  "Displays a notification (aka flash) message.

   :status   - one of the alert types from Bootstrap i.e. :success, :info, :warning or :danger
   :contents - content to show inside the notification"
  [{status :status contents :contents}]
  (when status
    [:div.alert {:class (str "alert-" (name status))} contents]))

(defn guide []
  [:div
   (component-info flash-message)
   (example "flash-message with info"
            [flash-message {:status :info
                            :contents "Hello world"}])

   (example "flash-message with error"
            [flash-message {:status :danger
                            :contents "You fail"}])])

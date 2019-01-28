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

(defn readonly-checkbox
  "Displays a checkbox."
  [checked?]
  (if checked?
    [:i.fa.fa-lg.fa-check-square.color1]
    [:i.fa.fa-lg.fa-square.color1-faint]))

(defn info-field
  "A component that shows a readonly field with title and value.

  Used for e.g. displaying applicant attributes."
  [title value]
  [:div.form-group
   [:label title]
   [:div.form-control value]])

(defn guide []
  [:div
   (component-info flash-message)
   (example "flash-message with info"
            [flash-message {:status :info
                            :contents "Hello world"}])

   (example "flash-message with error"
            [flash-message {:status :danger
                            :contents "You fail"}])
   (component-info readonly-checkbox)
   (example "readonly-checkbox unchecked"
            [readonly-checkbox false])
   (example "readonly-checkbox checked"
            [readonly-checkbox true])
   (component-info info-field)
   (example "info-field with data"
            [info-field "Name" "Bob Tester"])])

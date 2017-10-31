(ns rems.info-field
  (:require [rems.guide :refer :all]))

(defn component
  "A component that shows a readonly field with title and value.

  Used for e.g. displaying applicant attributes."
  [title value]
  [:div.form-group
   [:label title]
   [:input.form-control {:type "text" :value value :readonly true}]])

(defn guide
  []
  (list
   (component-info component)
   (example "info-field with data"
            (component "Name" "Bob Tester"))))

(ns rems.applicant-info
  (:require [rems.collapsible :as collapsible]
            [rems.context :refer [*active-role*
                                  *roles*]]
            [rems.guide :refer :all]
            [rems.role-switcher :refer [has-roles?]]))

(defn- info-field [title value]
  [:div.form-group
   [:label title]
   [:input.form-control {:type "text" :value value :readonly true}]])

(defn details [user-attributes]
  (when user-attributes
    (let [applicant-title (str "Applicant: " (get user-attributes "commonName"))]
      (if (has-roles? :approver)
        (list
          (collapsible/header "#applicant-info" false "applicant-info" applicant-title)
          [:form#applicant-info.collapse
           (for [[k v] user-attributes]
             (info-field k v)
             )])
        [:h3 applicant-title]))))

(defn guide
  []
  (list
    (example "General view"
             (details {"eppn" "developer@uu.id" "commonName" "Deve Loper"}))
    (example "Approver view"
             (binding [context/*roles* #{:approver}
                       context/*active-role* :approver]
               ;; Accordion is needed so that the +/- icons are shown in the guide page
               [:div#accordion
                (details {"eppn" "developer@uu.id" "commonName" "Deve Loper"})]))))

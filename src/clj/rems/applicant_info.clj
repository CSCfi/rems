(ns rems.applicant-info
  (:require [rems.collapsible :as collapsible]
            [rems.context :as context]
            [rems.guide :refer :all]
            [rems.role-switcher :refer [when-role]]
            [rems.util :refer [get-username]]))

(defn- info-field [title value]
  [:div.form-group
   [:label title]
   [:input.form-control {:type "text" :value value :readonly true}]])

(defn details [user-attributes]
  (let [applicant-title (str "Applicant: " (get-username user-attributes))]
    (collapsible/component "applicant-info"
                           false
                           applicant-title
                           (when-role :approver
                             [:form
                              (for [[k v] user-attributes]
                                (info-field k v)
                                )]))))

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

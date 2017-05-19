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
  (let [title (str "Applicant: " (get-username user-attributes))
        content (when-role :approver
                  [:form
                   (for [[k v] user-attributes]
                     (info-field k v)
                     )])]
    (collapsible/component "applicant-info"
                           false
                           title
                           content)))

(defn guide
  []
  (list
   (example "applicant-info for applicant shows no details"
            (details {"eppn" "developer@uu.id" "commonName" "Deve Loper"}))
   (example "applicant-info for approver shows attributes"
            (binding [context/*roles* #{:approver}
                      context/*active-role* :approver]
              ;; Accordion is needed so that the +/- icons are shown in the guide page
              [:div#accordion
               (details {"eppn" "developer@uu.id" "commonName" "Deve Loper"})]))))

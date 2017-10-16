(ns rems.applicant-info
  (:require [rems.collapsible :as collapsible]
            [rems.context :as context]
            [rems.guide :refer :all]
            [rems.roles :refer [when-roles]]
            [rems.text :refer :all]
            [rems.util :refer [get-username]]))

(defn- info-field [title value]
  [:div.form-group
   [:label title]
   [:input.form-control {:type "text" :value value :readonly true}]])

(defn details [id user-attributes]
  (let [title (str (text :t.applicant-info/applicant) ": " (get-username user-attributes))
        content (when-roles #{:approver :reviewer}
                  [:form
                   (for [[k v] user-attributes]
                     (info-field k v)
                     )])]
    (collapsible/component id
                           false
                           title
                           content)))

(defn guide
  []
  (list
   (example "applicant-info for applicant shows no details"
            (binding [context/*roles* #{:applicant}]
              (details "info1" {"eppn" "developer@uu.id" "commonName" "Deve Loper"})))
   (example "applicant-info for approver shows attributes"
            (binding [context/*roles* #{:approver}]
              (details "info2" {"eppn" "developer@uu.id" "commonName" "Deve Loper"})))))

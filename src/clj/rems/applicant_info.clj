(ns rems.applicant-info
  (:require [rems.collapsible :as collapsible]
            [rems.context :as context]
            [rems.guide :refer :all]
            [rems.info-field :as info-field]
            [rems.roles :refer [when-roles]]
            [rems.text :refer :all]
            [rems.util :refer [get-user-mail
                               get-username]]))

(defn details [id user-attributes]
  (collapsible/component
   {:id id
    :title (str (text :t.applicant-info/applicant))
    :always [:div.row
             [:div.col-md-6
              (info-field/component (text :t.applicant-info/username) (get-username user-attributes))]
             [:div.col-md-6
              (info-field/component (text :t.applicant-info/email) (get-user-mail user-attributes))]]
    :collapse (when-roles #{:approver :reviewer}
                [:form
                 (for [[k v] (dissoc user-attributes "commonName" "mail")]
                   (info-field/component k v))])}))

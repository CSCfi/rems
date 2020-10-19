(ns rems.user
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [rems.atoms :refer [info-field readonly-checkbox]]
            [rems.common.util :refer [index-by]]
            [rems.text :refer [text localized]]))

(defn attributes [attributes]
  (let [language @(rf/subscribe [:language])
        organization-by-id @(rf/subscribe [:organization-by-id])
        organization-name-if-known (fn [organization]
                                     (if-let [known-organization (organization-by-id (:organization/id organization))] ; comes from idp, maybe unknown
                                       (get-in known-organization [:organization/short-name language])
                                       (:organization/id organization)))
        other-attributes (dissoc attributes :name :userid :email :organizations :notification-email :researcher-status-by)
        extra-attributes (index-by [:attribute] (:oidc-extra-attributes @(rf/subscribe [:rems.config/config])))]
    (into [:div.user-attributes
           (when-let [user-id (:userid attributes)]
             [info-field (text :t.applicant-info/username) user-id {:inline? true}])
           (when-let [mail (:notification-email attributes)]
             [info-field (text :t.applicant-info/notification-email) mail {:inline? true}])
           (when-let [mail (:email attributes)]
             [info-field (text :t.applicant-info/email) mail {:inline? true}])
           (when-let [organizations (seq (:organizations attributes))]
             [info-field (text :t.applicant-info/organization) (str/join ", " (map organization-name-if-known organizations)) {:inline? true}])
           (when (#{:so :system} (:researcher-status-by attributes))
             [info-field (text :t.applicant-info/researcher-status) [readonly-checkbox {:value true}] {:inline? true}])]
          (for [[k v] other-attributes]
            (let [title (or (localized (get-in extra-attributes [(name k) :name]))
                            k)]
              [info-field title v {:inline? true}])))))

(ns rems.db.test-data-helpers
  (:require [clj-time.core :as time]
            [clojure.test :refer :all]
            [rems.api.services.catalogue :as catalogue]
            [rems.api.services.command :as command]
            [rems.api.services.form :as form]
            [rems.api.services.licenses :as licenses]
            [rems.api.services.organizations :as organizations]
            [rems.api.services.resource :as resource]
            [rems.api.services.workflow :as workflow]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.roles :as roles]
            [rems.db.test-data-users :refer :all]
            [rems.db.users :as users]
            [rems.testing-util :refer [with-user]])
  (:import [java.util UUID]))

;;; helpers for generating test data

(defn command! [command]
  (let [command (merge {:time (time/now)}
                       command)
        result (command/command! command)]
    (assert (not (:errors result))
            {:command command :result result})
    result))

(defn- transpose-localizations [m] ; TODO could get rid of?
  (->> m
       (mapcat (fn [[k1 v]]
                 (map (fn [[k2 v]]
                        [k1 k2 v])
                      v)))
       (reduce (fn [m [k1 k2 v]]
                 (assoc-in m [k2 k1] v))
               {})))

(deftest test-transpose-localizations
  (is (= {:en {:title "en", :url "www.com"}
          :fi {:title "fi", :url "www.fi"}
          :sv {:url "www.se"}}
         (transpose-localizations {:title {:en "en" :fi "fi"}
                                   :url {:en "www.com" :fi "www.fi" :sv "www.se"}
                                   :empty {}}))))

(defn create-user! [user-attributes & roles]
  (let [user (:eppn user-attributes)]
    (users/add-user-raw! user user-attributes)
    (doseq [role roles]
      (roles/add-role! user role))
    user))

(defn- create-owner! []
  (create-user! (get +fake-user-data+ "owner") :owner)
  "owner")

(defn create-organization! [{:keys [actor users]
                             :organization/keys [id name short-name owners review-emails]
                             :as command}]
  (let [actor (or actor (create-owner!))
        result (organizations/add-organization! actor
                                                {:organization/id (or id "default")
                                                 :organization/name (or name {:fi "Oletusorganisaatio" :en "The Default Organization" :sv "Standardorganisationen"})
                                                 :organization/short-name (or short-name {:fi "Oletus" :en "Default" :sv "Standard"})
                                                 :organization/owners (or owners
                                                                          (if users
                                                                            [{:userid (users :organization-owner1)} {:userid (users :organization-owner2)}]
                                                                            []))
                                                 :organization/review-emails (or review-emails [])})]
    (assert (:success result) {:command command :result result})
    (:organization/id result)))

(defn create-license! [{:keys [actor organization]
                        :license/keys [type title link text attachment-id]
                        :as command}]
  (let [actor (or actor (create-owner!))
        result (with-user actor
                 (licenses/create-license! {:licensetype (name (or type :text))
                                            :organization (or organization {:organization/id "default"})
                                            :localizations
                                            (transpose-localizations {:title title
                                                                      :textcontent (merge link text)
                                                                      :attachment-id attachment-id})}
                                           actor))]
    (assert (:success result) {:command command :result result})
    (:id result)))

(defn create-attachment-license! [{:keys [actor organization]}]
  (let [fi-attachment (:id (db/create-license-attachment! {:user (or actor "owner")
                                                           :filename "license-fi.txt"
                                                           :type "text/plain"
                                                           :data (.getBytes "Suomenkielinen lisenssi.")}))
        en-attachment (:id (db/create-license-attachment! {:user (or actor "owner")
                                                           :filename "license-en.txt"
                                                           :type "text/plain"
                                                           :data (.getBytes "License in English.")}))]
    (with-user actor
      (create-license! {:actor actor
                        :license/type :attachment
                        :organization (or organization {:organization/id "default"})
                        :license/title {:fi "Liitelisenssi" :en "Attachment license"}
                        :license/text {:fi "fi" :en "en"}
                        :license/attachment-id {:fi fi-attachment :en en-attachment}}))))

(defn create-form! [{:keys [actor organization]
                     :form/keys [title fields]
                     :as command}]
  (let [actor (or actor (create-owner!))
        result (with-user actor
                 (form/create-form! actor
                                    {:organization (or organization {:organization/id "default"})
                                     :form/title (or title "FORM")
                                     :form/fields (or fields [])}))]
    (assert (:success result) {:command command :result result})
    (:id result)))

(defn create-resource! [{:keys [actor organization resource-ext-id license-ids]
                         :as command}]
  (let [actor (or actor (create-owner!))
        result (with-user actor
                 (resource/create-resource! {:resid (or resource-ext-id (str "urn:uuid:" (UUID/randomUUID)))
                                             :organization (or organization {:organization/id "default"})
                                             :licenses (or license-ids [])}
                                            actor))]
    (assert (:success result) {:command command :result result})
    (:id result)))

(defn create-workflow! [{:keys [actor organization title type handlers forms]
                         :as command}]
  (let [actor (or actor (create-owner!))
        result (with-user actor
                 (workflow/create-workflow!
                  {:user-id actor
                   :organization (or organization {:organization/id "default"})
                   :title (or title "")
                   :type (or type :workflow/master)
                   :forms forms
                   :handlers
                   (or handlers
                       (do (create-user! (get +fake-user-data+ "developer"))
                           ["developer"]))}))]
    (assert (:success result) {:command command :result result})
    (:id result)))

(defn create-catalogue-item! [{:keys [actor title resource-id form-id workflow-id infourl organization]
                               :as command}]
  (let [actor (or actor (create-owner!))
        localizations (into {}
                            (for [lang (set (concat (keys title) (keys infourl)))]
                              [lang {:title (get title lang)
                                     :infourl (get infourl lang)}]))
        result (with-user actor
                 (catalogue/create-catalogue-item!
                  {:resid (or resource-id (create-resource! {:organization organization}))
                   :form (or form-id (create-form! {:organization organization}))
                   :organization (or organization {:organization/id "default"})
                   :wfid (or workflow-id (create-workflow! {:organization organization}))
                   :localizations (or localizations {})}))]
    (assert (:success result) {:command command :result result})
    (:id result)))

(defn create-application! [{:keys [catalogue-item-ids actor time]}]
  (:application-id (command! {:time (or time (time/now))
                              :type :application.command/create
                              :catalogue-item-ids (or catalogue-item-ids [(create-catalogue-item! {})])
                              :actor actor})))

(defn submit-application [app-id applicant]
  (command! {:type :application.command/submit
             :application-id app-id
             :actor applicant}))

(defn- base-command [{:keys [application-id actor time]}]
  (assert application-id)
  (assert actor)
  {:application-id application-id
   :actor actor
   :time (or time (time/now))})

(defn fill-form! [{:keys [application-id actor field-value optional-fields attachment] :as command}]
  (let [app (applications/get-application-for-user actor application-id)]
    (command! (assoc (base-command command)
                     :type :application.command/save-draft
                     :field-values (for [form (:application/forms app)
                                         field (:form/fields form)
                                         :when (or optional-fields
                                                   (not (:field/optional field)))]
                                     {:form (:form/id form)
                                      :field (:field/id field)
                                      :value (case (:field/type field)
                                               (:header :label) ""
                                               :date "2002-03-04"
                                               :email "user@example.com"
                                               :attachment (str attachment)
                                               (:option :multiselect) (:key (first (:field/options field)))
                                               (or field-value "x"))})))))

(defn accept-licenses! [{:keys [application-id actor] :as command}]
  (let [app (applications/get-application-for-user actor application-id)]
    (command! (assoc (base-command command)
                     :type :application.command/accept-licenses
                     :accepted-licenses (map :license/id (:application/licenses app))))))

(defn create-draft! [actor catalogue-item-ids description & [time]]
  (let [app-id (create-application! {:time time
                                     :catalogue-item-ids catalogue-item-ids
                                     :actor actor})]
    (fill-form! {:time time
                 :application-id app-id
                 :actor actor
                 :field-value description})
    (accept-licenses! {:time time
                       :application-id app-id
                       :actor actor})
    app-id))



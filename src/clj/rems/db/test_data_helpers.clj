(ns rems.db.test-data-helpers
  (:require [clj-time.core :as time]
            [medley.core :refer [assoc-some update-existing]]
            [clojure.test :refer [deftest is]]
            [com.rpl.specter :refer [ALL must transform]]
            [clojure.string]
            [rems.service.catalogue :as catalogue]
            [rems.service.category :as category]
            [rems.service.command :as command]
            [rems.service.form :as form]
            [rems.service.licenses :as licenses]
            [rems.service.organizations :as organizations]
            [rems.service.resource :as resource]
            [rems.service.workflow :as workflow]
            [rems.common.util :refer [fix-filename]]
            [rems.config]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.roles :as roles]
            [rems.db.organizations]
            [rems.db.test-data-users :refer [+fake-user-data+]]
            [rems.db.users :as users]
            [rems.db.user-mappings :as user-mappings]
            [rems.testing-util :refer [with-user]])
  (:import [java.util UUID]))

(defn select-config-langs [m]
  (select-keys m (:languages rems.config/env)))

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

(defn create-user! [user-attributes-and-mappings & roles]
  (let [mappings (:mappings user-attributes-and-mappings)
        user-attributes (dissoc user-attributes-and-mappings :mappings)
        user (:userid user-attributes)]
    (users/add-user-raw! user user-attributes)
    (doseq [[k v] mappings]
      (user-mappings/create-user-mapping! {:userid user :ext-id-attribute k :ext-id-value v}))
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
        result (organizations/add-organization!
                {:organization/id (or id "default")
                 :organization/name (select-config-langs
                                     (or name {:fi "Oletusorganisaatio"
                                               :en "The Default Organization"
                                               :sv "Standardorganisationen"}))
                 :organization/short-name (select-config-langs
                                           (or short-name {:fi "Oletus"
                                                           :en "Default"
                                                           :sv "Standard"}))
                 :organization/owners (or owners
                                          (if users
                                            [{:userid (users :organization-owner1)}
                                             {:userid (users :organization-owner2)}]
                                            []))
                 :organization/review-emails (->> (or review-emails [])
                                                  (mapv #(update-existing %
                                                                          :name
                                                                          select-config-langs)))})]
    (assert (:success result) {:command command :result result})
    (:organization/id result)))

(defn ensure-default-organization! []
  (when-not (rems.db.organizations/get-organization-by-id-raw "default")
    (create-organization! {}))
  {:organization/id "default"})

(defn create-license! [{:keys [actor organization]
                        :license/keys [type title link text attachment-id]
                        :as command}]
  (let [actor (or actor (create-owner!))
        result (with-user actor
                 (licenses/create-license!
                  {:licensetype (name (or type :text))
                   :organization (or organization (ensure-default-organization!))
                   :localizations (select-config-langs
                                   (transpose-localizations {:title title
                                                             :textcontent (merge link text)
                                                             :attachment-id attachment-id}))}))]
    (assert (:success result) {:command command :result result})
    (:id result)))

(defn create-attachment-license! [{:keys [actor organization]}]
  (let [langs (set (:languages rems.config/env))
        fi-attachment (when (:fi langs)
                        (:id (db/create-license-attachment!
                              {:user (or actor "owner")
                               :filename "license-fi.txt"
                               :type "text/plain"
                               :data (.getBytes "Suomenkielinen lisenssi.")
                               :start (time/now)})))
        en-attachment (when (:en langs)
                        (:id (db/create-license-attachment!
                              {:user (or actor "owner")
                               :filename "license-en.txt"
                               :type "text/plain"
                               :data (.getBytes "License in English.")
                               :start (time/now)})))]
    (with-user actor
      (create-license! {:actor actor
                        :license/type :attachment
                        :organization (or organization (ensure-default-organization!))
                        :license/title (select-config-langs {:fi "Liitelisenssi"
                                                             :en "Attachment license"})
                        :license/text (select-config-langs {:fi "fi" :en "en"})
                        :license/attachment-id (select-config-langs {:fi fi-attachment
                                                                     :en en-attachment})}))))

(defn create-form! [{:keys [actor organization]
                     :form/keys [internal-name external-title fields]
                     :as command}]
  (let [actor (or actor (create-owner!))
        result (with-user actor
                 (form/create-form!
                  {:organization (or organization (ensure-default-organization!))
                   :form/internal-name (or internal-name "FORM")
                   :form/external-title (select-config-langs
                                         (or external-title
                                             (into {}
                                                   (for [lang (:languages rems.config/env)]
                                                     [lang (str (name lang) " Form")]))))
                   :form/fields (->> (or fields [])
                                     (mapv #(update-existing %
                                                             :field/title
                                                             select-config-langs)))}))]
    (assert (:success result) {:command command :result result})
    (:id result)))

(defn create-resource! [{:keys [actor organization resource-ext-id license-ids]
                         :as command}]
  (let [actor (or actor (create-owner!))
        duo-data (->> (select-keys command [:resource/duo])
                      (transform [:resource/duo (must :duo/codes) ALL (must :more-info)]
                                 select-config-langs))
        result (with-user actor
                 (resource/create-resource!
                  (merge {:resid (or resource-ext-id (str "urn:uuid:" (UUID/randomUUID)))
                          :organization (or organization (ensure-default-organization!))
                          :licenses (or license-ids [])}
                         duo-data)))]
    (assert (:success result) {:command command :result result})
    (:id result)))

(defn create-workflow! [{:keys [actor organization title type handlers forms licenses]
                         :as command}]
  (let [actor (or actor (create-owner!))
        result (with-user actor
                 (workflow/create-workflow!
                  {:organization (or organization (ensure-default-organization!))
                   :title (or title "")
                   :type (or type :workflow/master)
                   :forms forms
                   :handlers (or handlers
                                 (do (create-user! (get +fake-user-data+ "developer"))
                                     ["developer"]))
                   :licenses (mapv (fn [id] {:license/id id}) licenses)}))]
    (assert (:success result) {:command command :result result})
    (:id result)))

(defn create-category! [{:keys [actor] :category/keys [title description children]
                         :as command}]
  (let [actor (or actor (create-owner!))
        result (with-user actor
                 (category/create-category!
                  (merge {:category/title (select-config-langs
                                           (or title {:en "Category"
                                                      :fi "Kategoria"
                                                      :sv "Kategori"}))}

                         (cond (false? description)
                               nil

                               (nil? description)
                               {:category/description (select-config-langs {:en "Category description"
                                                                            :fi "Kategorian kuvaus"
                                                                            :sv "Beskrivning av kategori"})}

                               :else
                               {:category/description (select-config-langs description)})

                         (when (seq children)
                           {:category/children children}))))]
    (assert (:success result) {:command command :result result})
    (:category/id result)))

(defn create-catalogue-item! [{:keys [actor title resource-id form-id workflow-id infourl organization start categories]
                               :as command}]
  (let [actor (or actor (create-owner!))
        localizations (into {}
                            (for [lang (set (concat (keys title) (keys infourl)))]
                              [lang {:title (get title lang)
                                     :infourl (get infourl lang)}]))
        result (with-user actor
                 (catalogue/create-catalogue-item!
                  (-> {:start (or start (time/now))
                       :resid (or resource-id (create-resource! {:organization organization}))
                       :form (if (contains? command :form-id) ; support :form-id nil
                               form-id
                               (create-form! {:organization organization}))
                       :organization (or organization (ensure-default-organization!))
                       :wfid (or workflow-id (create-workflow! {:organization organization}))
                       :localizations (select-config-langs (or localizations {}))
                       :enabled (:enabled command true)}
                      (assoc-some :categories categories))))]
    (assert (:success result) {:command command :result result})
    (:id result)))

(defn create-application! [{:keys [catalogue-item-ids actor time]}]
  (:application-id (command! {:time (or time (time/now))
                              :type :application.command/create
                              :catalogue-item-ids (or catalogue-item-ids [(create-catalogue-item! {})])
                              :actor actor})))

(defn submit-application [{:keys [application-id actor time]}]
  (command! {:time (or time (time/now))
             :type :application.command/submit
             :application-id application-id
             :actor actor}))

(defn- base-command [{:keys [application-id actor time]}]
  (assert application-id)
  (assert actor)
  {:application-id application-id
   :actor actor
   :time (or time (time/now))})

(defn fill-form! [{:keys [application-id actor field-value optional-fields attachment multiselect] :as command}]
  (let [app (applications/get-application-for-user actor application-id)]
    (command! (assoc (base-command command)
                     :type :application.command/save-draft
                     :field-values (for [form (:application/forms app)
                                         field (:form/fields form)
                                         :when (and (:field/visible field)
                                                    (or optional-fields
                                                        (not (:field/optional field))))]
                                     {:form (:form/id form)
                                      :field (:field/id field)
                                      :value (case (:field/type field)
                                               (:header :label) ""
                                               :date "2002-03-04"
                                               :email "user@example.com"
                                               :phone-number "+358451110000"
                                               :ip-address "142.250.74.110"
                                               :table (repeat 2 (for [column (:field/columns field)]
                                                                  {:column (:key column) :value field-value}))
                                               :attachment (str attachment)
                                               :option (:key (first (:field/options field)))
                                               :multiselect (or multiselect
                                                                (->> (:field/options field)
                                                                     (map :key)
                                                                     (clojure.string/join " ")))
                                               (or field-value "x"))})))))

(defn fill-duo-codes! [{:keys [application-id actor duos] :as command}]
  (let [app (applications/get-application-for-user actor application-id)]
    (command! (assoc (base-command command)
                     :type :application.command/save-draft
                     ;; copy existing forms so as to not override
                     :field-values (for [form (:application/forms app)
                                         field (:form/fields form)]
                                     {:form (:form/id form)
                                      :field (:field/id field)
                                      :value (:field/value field)})
                     :duo-codes duos))))

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

(defn create-attachment! [{:keys [actor application-id filename filetype data]}]
  (let [previous-attachments (db/get-attachments-for-application {:application-id application-id})
        filename (->> (mapv :filename previous-attachments)
                      (fix-filename (or filename "attachment.pdf")))
        attachment (db/save-attachment! {:application application-id
                                         :user actor
                                         :filename filename
                                         :type (or filetype "application/pdf")
                                         :data (.getBytes (or data ""))})]
    (:id attachment)))

(defn assert-no-existing-data! []
  (assert (empty? (db/get-organizations {}))
          "You have existing oranizations, refusing to continue. An empty database is needed.")
  (assert (empty? (db/get-application-events {}))
          "You have existing applications, refusing to continue. An empty database is needed.")
  (assert (empty? (db/get-catalogue-items {}))
          "You have existing catalogue items, refusing to continue. An empty database is needed."))

(defn invite-and-accept-member! [{:keys [actor application-id member]}]
  (command! {:type :application.command/invite-member
             :actor actor
             :application-id application-id
             :member (select-keys member [:email :name])})
  (command! {:type :application.command/accept-invitation
             :actor (:userid member)
             :application-id application-id
             :token (-> (rems.db.applications/get-application-internal application-id)
                        :application/events
                        last
                        :invitation/token)}))

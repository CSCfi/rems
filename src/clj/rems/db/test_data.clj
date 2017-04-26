(ns rems.db.test-data
  "Populating the database with nice test data."
  (:require [rems.context :as context]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.roles :as roles]
            [rems.util :refer [get-user-id]]))

(defn- create-users-and-roles! []
  ;; users provided by the fake login
  (db/add-user! {:user "developer" :userattrs nil})
  (roles/add-role! "developer" :applicant)
  (roles/add-role! "developer" :approver)
  (db/add-user! {:user "alice" :userattrs nil})
  (roles/add-role! "alice" :applicant)
  (db/add-user! {:user "bob" :userattrs nil})
  (roles/add-role! "bob" :approver)
  ;; a user to own things
  (db/add-user! {:user "owner" :userattrs nil})
  ;; users used on remsdemo
  (doseq [applicant ["RDapplicant1" "RDapplicant2"]]
    (db/add-user! {:user applicant :userattrs nil})
    (roles/add-role! applicant :applicant))
  (doseq [approver ["RDapprover1" "RDapprover2"]]
    (db/add-user! {:user approver :userattrs nil})
    (roles/add-role! approver :approver)))

(defn- create-basic-form! []
  "Creates a bilingual form with all supported field types. Returns id of the form meta."
  (let [meta (db/create-form-meta! {:title "metatitle" :user "owner"})
        form-en (db/create-form! {:title "Basic application" :user "owner"})
        form-fi (db/create-form! {:title "Yksinkertainen lomake" :user "owner"})

        name-fi (db/create-form-item!
                 {:title "Projektin nimi" :type "text" :inputprompt "Projekti"
                  :optional false :user "owner" :value 0})
        name-en (db/create-form-item!
                 {:title "Project name" :type "text" :inputprompt "Project"
                  :optional false :user "owner" :value 0})
        purpose-fi (db/create-form-item!
                    {:title "Projektin tarkoitus" :type "texta"
                     :inputprompt "Projektin tarkoitus on ..." :optional false
                     :user "owner" :value 0})
        purpose-en (db/create-form-item!
                    {:title "Purpose of the project" :type "texta"
                     :inputprompt "The purpose of the project is to ..." :optional false
                     :user "owner" :value 0})
        duration-en (db/create-form-item!
                     {:title "Duration of the project" :type "text"
                      :inputprompt "YYYY-YYYY" :optional true
                      :user "owner" :value 0})
        duration-fi (db/create-form-item!
                     {:title "Projektin kesto" :type "text"
                      :inputprompt "YYYY-YYYY" :optional true
                      :user "owner" :value 0})]
    (db/link-form-meta! {:meta (:id meta) :form (:id form-en) :lang "en" :user "owner"})
    (db/link-form-meta! {:meta (:id meta) :form (:id form-fi) :lang "fi" :user "owner"})

    ;; link out of order for less predictable row ids
    (db/link-form-item! {:form (:id form-en) :itemorder 3 :optional false :item (:id purpose-en) :user "owner"})
    (db/link-form-item! {:form (:id form-en) :itemorder 2 :optional true :item (:id duration-en) :user "owner"})
    (db/link-form-item! {:form (:id form-en) :itemorder 1 :optional false :item (:id name-en) :user "owner"})
    (db/link-form-item! {:form (:id form-fi) :itemorder 1 :optional false :item (:id name-fi) :user "owner"})
    (db/link-form-item! {:form (:id form-fi) :itemorder 3 :optional false :item (:id purpose-fi) :user "owner"})
    (db/link-form-item! {:form (:id form-fi) :itemorder 2 :optional true :item (:id duration-fi) :user "owner"})
    (:id meta)))

(defn- create-workflows! []
  (let [minimal (:id (db/create-workflow! {:owneruserid "owner" :modifieruserid "owner":title "minimal" :fnlround 0}))
        simple (:id (db/create-workflow! {:owneruserid "owner" :modifieruserid "owner" :title "simple" :fnlround 0}))
        two-round (:id (db/create-workflow! {:owneruserid "owner" :modifieruserid "owner" :title "two rounds" :fnlround 1}))
        different (:id (db/create-workflow! {:owneruserid "owner" :modifieruserid "owner" :title "two rounds, different approvers" :fnlround 1}))]
    ;; either bob or developer can approve
    (db/create-workflow-approver! {:wfid simple :appruserid "developer" :round 0})
    (db/create-workflow-approver! {:wfid simple :appruserid "bob" :round 0})
    ;; only developer can approve
    (db/create-workflow-approver! {:wfid two-round :appruserid "developer" :round 0})
    (db/create-workflow-approver! {:wfid two-round :appruserid "developer" :round 1})
    ;; first bob, then developer
    (db/create-workflow-approver! {:wfid different :appruserid "bob" :round 0})
    (db/create-workflow-approver! {:wfid different :appruserid "developer" :round 1})

    ;; attach both kinds of licenses to all workflows
    (let [link (:id (db/create-license!
                     {:modifieruserid "owner" :owneruserid "owner" :title "non-localized license"
                      :type "link" :textcontent "http://invalid"}))
          text (:id (db/create-license!
                     {:modifieruserid "owner" :owneruserid "owner" :title "non-localized license"
                      :type "text" :textcontent "non-localized content"}))]
      (db/create-license-localization!
       {:licid link :langcode "en" :title "CC Attribution 4.0"
        :textcontent "https://creativecommons.org/licenses/by/4.0/legalcode"})
      (db/create-license-localization!
       {:licid link :langcode "fi" :title "CC Nimeä 4.0"
        :textcontent "https://creativecommons.org/licenses/by/4.0/legalcode.fi"})
      (db/create-license-localization!
       {:licid text :langcode "fi" :title "Lisenssi"
        :textcontent (apply str (repeat 10 "Suomenkielinen lisenssiteksti. "))})
      (db/create-license-localization!
       {:licid text :langcode "en" :title "License"
        :textcontent (apply str (repeat 10 "License text in English. "))})

      (doseq [wf [minimal simple two-round different]]
        (db/create-workflow-license! {:wfid wf :licid link :round 0})
        (db/create-workflow-license! {:wfid wf :licid text :round 0})))

    {:minimal minimal
     :simple simple
     :two-round two-round
     :different different}))

(defn- create-catalogue-item! [resource workflow form localizations]
  (let [id (:id (db/create-catalogue-item!
                 {:title "non-localized title" :resid resource :wfid workflow :form form}))]
    (doseq [[lang title] localizations]
      (db/create-catalogue-item-localization! {:id id :langcode lang :title title}))
    id))

(defn- create-draft! [catalogue-id field-value]
  (let [app-id (applications/create-new-draft catalogue-id)
        form (binding [context/*lang* :en]
               (applications/get-form-for catalogue-id))]
    (doseq [{item-id :id} (:items form)]
      (db/save-field-value! {:application app-id :form (:id form)
                             :item item-id :user (get-user-id) :value field-value}))
    (doseq [{license-id :id} (:licenses form)]
      (db/save-license-approval! {:catappid app-id
                                  :round 0
                                  :licid license-id
                                  :actoruserid (get-user-id)
                                  :state "approved"}))
    app-id))

(defn- create-applications! [item user]
  (binding [context/*user* {"eppn" user}]
    (create-draft! item "draft application")
    (doto (create-draft! item "applied application")
      applications/submit-application)
    (doto (create-draft! item "rejected application")
      applications/submit-application
      (applications/reject-application 0 "comment for rejection"))
    (doto (create-draft! item "accepted application")
      applications/submit-application
      (applications/approve-application 0 "comment for approval"))
    (doto (create-draft! item "returned application")
      applications/submit-application
      (applications/return-application 0 "comment for return"))))

(defn create-test-data! []
  (create-users-and-roles!)
  (db/create-resource! {:id 1 :resid "http://urn.fi/urn:nbn:fi:lb-201403262" :prefix "nbn" :modifieruserid 1})
  (let [meta (create-basic-form!)
        workflows (create-workflows!)
        minimal (create-catalogue-item! 1 (:minimal workflows) meta
                                       {"en" "ELFA Corpus, direct approval"
                                        "fi" "ELFA-korpus, suora hyväksyntä"})
        simple (create-catalogue-item! 1 (:simple workflows) meta
                                       {"en" "ELFA Corpus, one approval"
                                        "fi" "ELFA-korpus, yksi hyväksyntä"})
        different (create-catalogue-item! 1 (:different workflows) meta
                                          {"en" "ELFA Corpus, two rounds of approval by different approvers"
                                           "fi" "ELFA-korpus, kaksi hyväksyntäkierrosta eri hyväksyjillä"})]
    (create-applications! simple "developer")))

(ns rems.db.test-data
  "Populating the database with nice test data."
  (:require [rems.db.core :as db]
            [rems.db.roles :as roles]))

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
  (db/add-user! {:user "owner" :userattrs nil}))

(defn- create-basic-form! []
  "Creates a bilingual form with all supported field types. Returns id of the form meta."
  (let [meta (db/create-form-meta! {:title "metatitle" :user "owner"})
        form-en (db/create-form! {:title "Yksinkertainen lomake" :user "owner"})
        form-fi (db/create-form! {:title "Basic application" :user "owner"})

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

(defn create-test-data! []
  (create-users-and-roles!)
  (let [meta (create-basic-form!)]
    (db/create-resource! {:id 1 :resid "http://urn.fi/urn:nbn:fi:lb-201403262" :prefix "nbn" :modifieruserid 1})
    (db/create-catalogue-item! {:title "ELFA Corpus"
                                :form (:id meta)
                                :resid 1
                                :wfid nil})
    (db/create-catalogue-item! {:title "B"
                                :form nil
                                :resid nil
                                :wfid nil})))

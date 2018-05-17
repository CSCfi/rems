(ns rems.db.test-data
  "Populating the database with nice test data."
  (:require [clj-time.core :as time]
            [rems.context :as context]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.roles :as roles]
            [rems.db.users :as users]
            [rems.db.workflow-actors :as actors]
            [rems.locales :as locales]
            [rems.util :refer [get-user-id]]))

(def +fake-user-data+
  {"developer" {"eppn" "developer" "mail" "deve@lo.per" "commonName" "Deve Loper"}
   "alice" {"eppn" "alice" "mail" "a@li.ce" "commonName" "Alice Applicant"}
   "bob" {"eppn" "bob" "mail" "b@o.b" "commonName" "Bob Approver"}
   "carl" {"eppn" "carl" "mail" "c@a.rl" "commonName" "Carl Reviewer"}
   "owner" {"eppn" "owner" "mail" "ow@n.er" "commonName" "Own Er"}})

(defn- create-users-and-roles! []
  ;; users provided by the fake login
  (users/add-user! "developer" (+fake-user-data+ "developer"))
  (roles/add-role! "developer" :applicant)
  (roles/add-role! "developer" :approver)
  (users/add-user! "alice" (+fake-user-data+ "alice"))
  (roles/add-role! "alice" :applicant)
  (users/add-user! "bob" (+fake-user-data+ "bob"))
  (roles/add-role! "bob" :approver)
  (users/add-user! "carl" (+fake-user-data+ "carl"))
  (roles/add-role! "carl" :reviewer)
  ;; a user to own things
  (users/add-user! "owner" (+fake-user-data+ "owner"))
  (roles/add-role! "owner" :owner))

(defn- create-demo-users-and-roles! []
  ;; a user to own things
  (db/add-user! {:user "owner" :userattrs nil})
  ;; users used on remsdemo
  (doseq [applicant ["RDapplicant1@funet.fi" "RDapplicant2@funet.fi"]]
    (db/add-user! {:user applicant :userattrs nil})
    (roles/add-role! applicant :applicant))
  (doseq [approver ["RDapprover1@funet.fi" "RDapprover2@funet.fi"]]
    (db/add-user! {:user approver :userattrs nil})
    (roles/add-role! approver :approver)
    (roles/add-role! approver :applicant))
  (let [reviewer "RDreview@funet.fi"]
    (db/add-user! {:user reviewer :userattrs nil})
    (roles/add-role! reviewer :reviewer)))

(defn- create-basic-form!
  "Creates a bilingual form with all supported field types. Returns id of the form meta."
  []
  (let [form (db/create-form! {:title "Yksinkertainen lomake" :user "owner"})

        name (db/create-form-item!
              {:type "text" :optional false :user "owner" :value 0})
        purpose (db/create-form-item!
                 {:type "texta" :optional false :user "owner" :value 0})
        duration (db/create-form-item!
                  {:type "text" :optional true :user "owner" :value 0})]
    ;; link out of order for less predictable row ids
    (db/link-form-item! {:form (:id form) :itemorder 1 :optional false :item (:id name) :user "owner"})
    (db/link-form-item! {:form (:id form) :itemorder 3 :optional false :item (:id purpose) :user "owner"})
    (db/link-form-item! {:form (:id form) :itemorder 2 :optional true :item (:id duration) :user "owner"})
    ;; localize
    (db/localize-form-item! {:item (:id name) :langcode "fi" :title "Projektin nimi" :inputprompt "Projekti"})
    (db/localize-form-item! {:item (:id name) :langcode "en" :title "Project name" :inputprompt "Project"})
    (db/localize-form-item! {:item (:id purpose) :langcode "fi"
                             :title "Projektin tarkoitus"
                             :inputprompt "Projektin tarkoitus on ..."})
    (db/localize-form-item! {:item (:id purpose) :langcode "en"
                             :title "Purpose of the project"
                             :inputprompt "The purpose of the project is to ..."})
    (db/localize-form-item! {:item (:id duration) :langcode "fi" :title "Projektin kesto" :inputprompt "YYYY-YYYY"})
    (db/localize-form-item! {:item (:id duration) :langcode "en" :title "Duration of the project" :inputprompt "YYYY-YYYY"})

    (:id form)))

(defn- create-workflows! [user1 user2 user3]
  (let [minimal (:id (db/create-workflow! {:owneruserid "owner" :modifieruserid "owner":title "minimal" :fnlround 0}))
        simple (:id (db/create-workflow! {:owneruserid "owner" :modifieruserid "owner" :title "simple" :fnlround 0}))
        with-review (:id (db/create-workflow! {:owneruserid "owner" :modifieruserid "owner" :title "with review" :fnlround 1}))
        two-round (:id (db/create-workflow! {:owneruserid "owner" :modifieruserid "owner" :title "two rounds" :fnlround 1}))
        different (:id (db/create-workflow! {:owneruserid "owner" :modifieruserid "owner" :title "two rounds, different approvers" :fnlround 1}))]
    ;; either user1 or user2 can approve
    (actors/add-approver! simple user1 0)
    (actors/add-approver! simple user2 0)
    ;; first user3 reviews, then user1 can approve
    (actors/add-reviewer! with-review user3 0)
    (actors/add-approver! with-review user1 1)
    ;; only user1 can approve
    (actors/add-approver! two-round user1 0)
    (actors/add-approver! two-round user1 1)
    ;; first user2, then user1
    (actors/add-approver! different user2 0)
    (actors/add-approver! different user1 1)

    ;; attach both kinds of licenses to all workflows
    (let [link (:id (db/create-license!
                     {:modifieruserid "owner" :owneruserid "owner" :title "non-localized link license"
                      :type "link" :textcontent "http://invalid"}))
          text (:id (db/create-license!
                     {:modifieruserid "owner" :owneruserid "owner" :title "non-localized text license"
                      :type "text" :textcontent "non-localized content"}))]
      (db/create-license-localization!
       {:licid link :langcode "en" :title "CC Attribution 4.0"
        :textcontent "https://creativecommons.org/licenses/by/4.0/legalcode"})
      (db/create-license-localization!
       {:licid link :langcode "fi" :title "CC Nimeä 4.0"
        :textcontent "https://creativecommons.org/licenses/by/4.0/legalcode.fi"})
      (db/create-license-localization!
       {:licid text :langcode "fi" :title "Yleiset käyttöehdot"
        :textcontent (apply str (repeat 10 "Suomenkielinen lisenssiteksti. "))})
      (db/create-license-localization!
       {:licid text :langcode "en" :title "General Terms of Use"
        :textcontent (apply str (repeat 10 "License text in English. "))})

      (doseq [wfid [minimal simple with-review two-round different]]
        (db/create-workflow-license! {:wfid wfid :licid link :round 0})
        (db/create-workflow-license! {:wfid wfid :licid text :round 0})
        (db/set-workflow-license-validity! {:licid link :start (time/minus (time/now) (time/years 1)) :end nil})
        (db/set-workflow-license-validity! {:licid text :start (time/minus (time/now) (time/years 1)) :end nil})))

    {:minimal minimal
     :simple simple
     :with-review with-review
     :two-round two-round
     :different different}))

(defn- create-resource-license! [resid text]
  (let [licid (:id (db/create-license!
                    {:modifieruserid "owner" :owneruserid "owner" :title "resource license"
                     :type "link" :textcontent "http://invalid"}))]
    (db/create-license-localization!
     {:licid licid :langcode "en" :title (str text " (en)")
      :textcontent "https://www.apache.org/licenses/LICENSE-2.0"})
    (db/create-license-localization!
     {:licid licid :langcode "fi" :title (str text " (fi)")
      :textcontent "https://www.apache.org/licenses/LICENSE-2.0"})
    (db/create-resource-license! {:resid resid :licid licid})
    (db/set-resource-license-validity! {:licid licid :start (time/minus (time/now) (time/years 1)) :end nil})
    licid))

(defn- create-catalogue-item! [resource workflow form localizations]
  (let [id (:id (db/create-catalogue-item!
                 {:title "non-localized title" :resid resource :wfid workflow :form form}))]
    (doseq [[lang title] localizations]
      (db/create-catalogue-item-localization! {:id id :langcode lang :title title}))
    id))

(defn- create-draft! [catids wfid field-value & [now]]
  (let [app-id (applications/create-new-draft-at-time wfid (or now (time/now)))
        _ (if (vector? catids)
            (doseq [catid catids]
              (db/add-application-item! {:application app-id :item catid}))
            (db/add-application-item! {:application app-id :item catids}))
        form (binding [context/*lang* :en]
               (applications/get-form-for app-id))]
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

(defn- create-applications! [catid wfid applicant approver]
  (binding [context/*tempura* locales/tconfig
            context/*user* {"eppn" applicant}]
    (create-draft! catid wfid "draft application")
    (applications/submit-application (create-draft! catid wfid "applied application"))
    (let [application (create-draft! catid wfid "rejected application")]
      (applications/submit-application application)
      (binding [context/*user* {"eppn" approver}]
        (applications/reject-application application 0 "comment for rejection")))
    (let [application (create-draft! catid wfid "accepted application")]
      (applications/submit-application application)
      (binding [context/*user* {"eppn" approver}]
        (applications/approve-application application 0 "comment for approval")))
    (let [application (create-draft! catid wfid "returned application")]
      (applications/submit-application application)
      (binding [context/*user* {"eppn" approver}]
        (applications/return-application application 0 "comment for return")))))

(defn- create-disabled-applications! [catid wfid applicant approver]
  (binding [context/*tempura* locales/tconfig
            context/*user* {"eppn" applicant}]
    (let [application (create-draft! catid wfid "draft with disabled item")])
    (let [application (create-draft! catid wfid "approved application with disabled item")]
      (applications/submit-application application)
      (binding [context/*user* {"eppn" approver}]
        (applications/approve-application application 0 "comment for approval")))))

(defn- create-bundled-application! [catid catid2 wfid applicant approver]
  (binding [context/*tempura* locales/tconfig
            context/*user* {"eppn" applicant}]
    (let [app-id (create-draft! [catid catid2] wfid "bundled application")]
      (applications/submit-application app-id)
      (binding [context/*user* {"eppn" approver}]
        (applications/return-application app-id 0 "comment for return"))
      (applications/submit-application app-id))))

(defn- create-review-application! [catid wfid applicant reviewer approver]
  (binding [context/*tempura* locales/tconfig
            context/*user* {"eppn" applicant}]
    (let [app-id (create-draft! catid wfid "application with review")]
      (applications/submit-application app-id)
      (binding [context/*user* {"eppn" reviewer}]
        (applications/review-application app-id 0 "comment for review"))
      (binding [context/*user* {"eppn" approver}]
        (applications/approve-application app-id 1 "comment for approval")))))

(defn- create-application-with-expired-resource-license! [wfid form applicant-user]
  (let [resource-id (:id (db/create-resource! {:resid "Resource that has expired license" :prefix "nbn" :modifieruserid 1}))
        year-ago (time/minus (time/now) (time/years 1))
        yesterday (time/minus (time/now) (time/days 1))
        licid-expired (create-resource-license! resource-id "License that has expired")
        _ (db/set-resource-license-validity! {:licid licid-expired :start year-ago :end yesterday})
        item-with-expired-license (create-catalogue-item! resource-id wfid form {"en" "Resource with expired resource license"
                                                                                 "fi" "Resurssi jolla on vanhentunut resurssilisenssi"})]
    (binding [context/*tempura* locales/tconfig
              context/*user* {"eppn" applicant-user}]
      (applications/submit-application (create-draft! item-with-expired-license wfid "applied when license was valid that has since expired" (time/minus (time/now) (time/days 2)))))))

(defn- create-application-before-new-resource-license! [wfid form applicant-user]
  (let [resource-id (:id (db/create-resource! {:resid "Resource that has a new resource license" :prefix "nbn" :modifieruserid 1}))
        yesterday (time/minus (time/now) (time/days 1))
        licid-new (create-resource-license! resource-id "License that was just created")
        _ (db/set-resource-license-validity! {:licid licid-new :start (time/now) :end nil})
        item-without-new-license (create-catalogue-item! resource-id wfid form {"en" "Resource with just created new resource license"
                                                                                "fi" "Resurssi jolla on uusi resurssilisenssi"})]
    (binding [context/*tempura* locales/tconfig
              context/*user* {"eppn" applicant-user}]
      (applications/submit-application (create-draft! item-without-new-license wfid "applied before license was valid" (time/minus (time/now) (time/days 2)))))))

(defn create-test-data! []
  (db/add-api-key! {:apikey 42 :comment "test data"})
  (create-users-and-roles!)
  (let [res1 (:id (db/create-resource! {:resid "http://urn.fi/urn:nbn:fi:lb-201403262" :prefix "nbn" :modifieruserid 1}))
        res2 (:id (db/create-resource! {:resid "Extra Data" :prefix "nbn" :modifieruserid 1}))
        form (create-basic-form!)
        workflows (create-workflows! "developer" "bob" "carl")
        minimal (create-catalogue-item! res1 (:minimal workflows) form
                                        {"en" "ELFA Corpus, direct approval"
                                         "fi" "ELFA-korpus, suora hyväksyntä"})
        simple (create-catalogue-item! res1 (:simple workflows) form
                                       {"en" "ELFA Corpus, one approval"
                                        "fi" "ELFA-korpus, yksi hyväksyntä"})
        bundable (create-catalogue-item! res2 (:simple workflows) form
                                         {"en" "ELFA Corpus, one approval (extra data)"
                                          "fi" "ELFA-korpus, yksi hyväksyntä (lisäpaketti)"})
        with-review (create-catalogue-item! res1 (:with-review workflows) form
                                            {"en" "ELFA Corpus, with review"
                                             "fi" "ELFA-korpus, katselmoinnilla"})
        different (create-catalogue-item! res1 (:different workflows) form
                                          {"en" "ELFA Corpus, two rounds of approval by different approvers"
                                           "fi" "ELFA-korpus, kaksi hyväksyntäkierrosta eri hyväksyjillä"})
        disabled (create-catalogue-item! res1 (:simple workflows) form
                                         {"en" "ELFA Corpus, one approval (extra data, disabled)"
                                          "fi" "ELFA-korpus, yksi hyväksyntä (lisäpaketti, pois käytöstä)"})]
    (create-resource-license! res2 "Some test license")
    (db/set-catalogue-item-state! {:item disabled :state "disabled" :user "developer"})
    (create-applications! simple (:simple workflows) "developer" "developer")
    (create-disabled-applications! disabled (:simple workflows) "developer" "developer")
    (create-bundled-application! simple bundable (:simple workflows) "alice" "developer")
    (create-review-application! with-review (:with-review workflows) "alice" "carl" "developer")
    (create-application-with-expired-resource-license! (:simple workflows) form "alice")
    (create-application-before-new-resource-license!  (:simple workflows) form "alice")))

(defn create-demo-data! []
  (create-demo-users-and-roles!)
  (let [res1 (:id (db/create-resource! {:resid "http://urn.fi/urn:nbn:fi:lb-201403262" :prefix "nbn" :modifieruserid 1}))
        res2 (:id (db/create-resource! {:resid "Extra Data" :prefix "nbn" :modifieruserid 1}))
        form (create-basic-form!)
        workflows (create-workflows! "RDapprover1@funet.fi" "RDapprover2@funet.fi" "RDreview@funet.fi")
        minimal (create-catalogue-item! res1 (:minimal workflows) form
                                        {"en" "ELFA Corpus, direct approval"
                                         "fi" "ELFA-korpus, suora hyväksyntä"})
        simple (create-catalogue-item! res1 (:simple workflows) form
                                       {"en" "ELFA Corpus, one approval"
                                        "fi" "ELFA-korpus, yksi hyväksyntä"})
        bundable (create-catalogue-item! res2 (:simple workflows) form
                                         {"en" "ELFA Corpus, one approval (extra data)"
                                          "fi" "ELFA-korpus, yksi hyväksyntä (lisäpaketti)"})
        with-review (create-catalogue-item! res1 (:with-review workflows) form
                                            {"en" "ELFA Corpus, with review"
                                             "fi" "ELFA-korpus, katselmoinnilla"})
        different (create-catalogue-item! res1 (:different workflows) form
                                          {"en" "ELFA Corpus, two rounds of approval by different approvers"
                                           "fi" "ELFA-korpus, kaksi hyväksyntäkierrosta eri hyväksyjillä"})
        disabled (create-catalogue-item! res1 (:simple workflows) form
                                         {"en" "ELFA Corpus, one approval (extra data, disabled)"
                                          "fi" "ELFA-korpus, yksi hyväksyntä (lisäpaketti, pois käytöstä)"})]
    (create-resource-license! res2 "Some demo license")
    (db/set-catalogue-item-state! {:item disabled :state "disabled" :user "developer"})
    (create-applications! simple (:simple workflows) "RDapplicant1@funet.fi" "RDapprover1@funet.fi")
    (create-disabled-applications! disabled (:simple workflows) "RDapplicant1@funet.fi" "RDapprover1@funet.fi")
    (create-bundled-application! simple bundable (:simple workflows) "RDapplicant2@funet.fi" "RDapprover1@funet.fi")
    (create-review-application! with-review (:with-review workflows) "RDapplicant1@funet.fi" "RDreview@funet.fi" "RDapprover1@funet.fi")
    (create-application-with-expired-resource-license! (:simple workflows) form "RDapplicant1@funet.fi")
    (create-application-before-new-resource-license!  (:simple workflows) form "RDapplicant1@funet.fi")))

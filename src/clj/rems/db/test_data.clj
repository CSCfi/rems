(ns rems.db.test-data
  "Populating the database with nice test data."
  (:require [clj-time.core :as time]
            [rems.context :as context]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.roles :as roles]
            [rems.db.users :as users]
            [rems.db.workflow :as workflow]
            [rems.db.workflow-actors :as actors]
            [rems.locales :as locales]
            [rems.util :refer [get-user-id]]))

(def +fake-users+
  {:applicant1 "alice"
   :approver1 "developer"
   :approver2 "bob"
   :owner "owner"
   :reviewer "carl"})

(def +fake-user-data+
  {"developer" {"eppn" "developer" "mail" "deve@lo.per" "commonName" "Deve Loper"}
   "alice" {"eppn" "alice" "mail" "a@li.ce" "commonName" "Alice Applicant"}
   "bob" {"eppn" "bob" "mail" "b@o.b" "commonName" "Bob Approver"}
   "carl" {"eppn" "carl" "mail" "c@a.rl" "commonName" "Carl Reviewer"}
   "owner" {"eppn" "owner" "mail" "ow@n.er" "commonName" "Own Er"}})

(def +demo-users+
  {:applicant1 "RDapplicant1@funet.fi"
   :applicant2 "RDapplicant2@funet.fi"
   :approver1 "RDapprover1@funet.fi"
   :approver2 "RDapprover2@funet.fi"
   :owner "RDowner@funet.fi"
   :reviewer "RDreview@funet.fi"})

(def +demo-user-data+
  {"RDapplicant1@funet.fi" {"eppn" "RDapplicant1@funet.fi" "mail" "RDapplicant1.test@test_example.org" "commonName" "RDapplicant1 REMSDEMO1"}
   "RDapplicant2@funet.fi" {"eppn" "RDapplicant2@funet.fi" "mail" "RDapplicant2.test@test_example.org" "commonName" "RDapplicant2 REMSDEMO"}
   "RDapprover1@funet.fi" {"eppn" "RDapprover1@funet.fi" "mail" "RDapprover1.test@rems_example.org" "commonName" "RDapprover1 REMSDEMO"}
   "RDapprover2@funet.fi" {"eppn" "RDapprover2@funet.fi" "mail" "RDapprover2.test@rems_example.org" "commonName" "RDapprover2 REMSDEMO"}
   "RDreview@funet.fi" {"eppn" "RDreview@funet.fi" "mail" "RDreview.test@rems_example.org" "commonName" "RDreview REMSDEMO"}
   "RDowner@funet.fi" {"eppn" "RDowner@funet.fi" "mail" "RDowner.test@test_example.org" "commonName" "RDowner REMSDEMO"}})

(defn- create-users-and-roles! []
  ;; users provided by the fake login
  (users/add-user! (+fake-users+ :approver1) (+fake-user-data+ (+fake-users+ :approver1)))
  (roles/add-role! (+fake-users+ :approver1) :applicant)
  (roles/add-role! (+fake-users+ :approver1) :approver)
  (users/add-user! (+fake-users+ :applicant1) (+fake-user-data+ (+fake-users+ :applicant1)))
  (roles/add-role! (+fake-users+ :applicant1) :applicant)
  (users/add-user! (+fake-users+ :approver2) (+fake-user-data+ (+fake-users+ :approver2)))
  (roles/add-role! (+fake-users+ :approver2) :approver)
  (users/add-user! (+fake-users+ :reviewer) (+fake-user-data+ (+fake-users+ :reviewer)))
  (roles/add-role! (+fake-users+ :reviewer) :reviewer)
  ;; a user to own things
  (users/add-user! (+fake-users+ :owner) (+fake-user-data+ (+fake-users+ :owner)))
  (roles/add-role! (+fake-users+ :owner) :owner)
  ;; invalid user for tests
  (db/add-user! {:user "invalid" :userattrs nil}))

(defn- create-demo-users-and-roles! []
  ;; users used on remsdemo
  (doseq [applicant [(+demo-users+ :applicant1) (+demo-users+ :applicant2)]]
    (users/add-user! applicant (+demo-user-data+ applicant))
    (roles/add-role! applicant :applicant))
  (doseq [approver [(+demo-users+ :approver1) (+demo-users+ :approver2)]]
    (users/add-user! approver (+demo-user-data+ approver))
    (roles/add-role! approver :approver)
    (roles/add-role! approver :applicant))
  (let [reviewer (+demo-users+ :reviewer)]
    (users/add-user! reviewer (+demo-user-data+ reviewer))
    (roles/add-role! reviewer :reviewer))
  ;; a user to own things
  (let [owner (+demo-users+ :owner)]
    (users/add-user! owner (+demo-user-data+ owner))
    (roles/add-role! owner :owner)))

(defn- create-expired-form! []
  (let [yesterday (time/minus (time/now) (time/days 1))]
    ;; only used from create-test-data!
    (db/create-form! {:organization "nbn" :title "Expired form, should not be seen" :user (+fake-users+ :owner) :endt yesterday})))

(defn- create-expired-license! []
  (let [owner (+fake-users+ :owner) ; only used from create-test-data!
        yesterday (time/minus (time/now) (time/days 1))]
    (db/create-license! {:modifieruserid owner :owneruserid owner :title "expired license" :type "link" :textcontent "http://expired" :endt yesterday})))

(defn- create-basic-form!
  "Creates a bilingual form with all supported field types. Returns id of the form meta."
  [users]
  (let [owner (users :owner)
        form (db/create-form! {:organization "nbn" :title "Yksinkertainen lomake" :user owner})
        name (db/create-form-item! {:type "text" :optional false :user owner :value 0})
        purpose (db/create-form-item! {:type "texta" :optional false :user owner :value 0})
        start-date (db/create-form-item! {:type "date" :optional true :user owner :value 0})
        expired (db/create-form-item! {:type "text" :optional true :user owner :value 0})
        plan (db/create-form-item! {:type "attachment" :optional true :user owner :value 0})]
    (db/end-form-item! {:id (:id expired)})
    ;; link out of order for less predictable row ids
    (db/link-form-item! {:form (:id form) :itemorder 1 :optional false :item (:id name) :user owner})
    (db/link-form-item! {:form (:id form) :itemorder 3 :optional false :item (:id purpose) :user owner})
    (db/link-form-item! {:form (:id form) :itemorder 2 :optional true :item (:id start-date) :user owner})
    (db/link-form-item! {:form (:id form) :itemorder 4 :optional true :item (:id plan) :user owner})
    ;; localize
    (db/localize-form-item! {:item (:id name) :langcode "fi" :title "Projektin nimi" :inputprompt "Projekti"})
    (db/localize-form-item! {:item (:id name) :langcode "en" :title "Project name" :inputprompt "Project"})
    (db/localize-form-item! {:item (:id purpose) :langcode "fi"
                             :title "Projektin tarkoitus"
                             :inputprompt "Projektin tarkoitus on ..."})
    (db/localize-form-item! {:item (:id purpose) :langcode "en"
                             :title "Purpose of the project"
                             :inputprompt "The purpose of the project is to ..."})
    (db/localize-form-item! {:item (:id start-date) :langcode "fi" :title "Projektin aloituspäivä" :inputprompt ""})
    (db/localize-form-item! {:item (:id start-date) :langcode "en" :title "Start date of the project" :inputprompt ""})
    (db/localize-form-item! {:item (:id expired) :langcode "en" :title "Expired form item" :inputprompt ""})
    (db/localize-form-item! {:item (:id plan) :langcode "fi" :title "Projektisuunnitelma" :inputprompt ""})
    (db/localize-form-item! {:item (:id plan) :langcode "en" :title "Project plan" :inputprompt ""})

    (:id form)))

(defn- create-workflows! [users]
  (let [approver1 (users :approver1)
        approver2 (users :approver2)
        reviewer (users :reviewer)
        owner (users :owner)
        minimal (:id (db/create-workflow! {:organization "nbn" :owneruserid owner :modifieruserid owner :title "minimal" :fnlround 0}))
        simple (:id (db/create-workflow! {:organization "nbn" :owneruserid owner :modifieruserid owner :title "simple" :fnlround 0}))
        with-review (:id (db/create-workflow! {:organization "nbn" :owneruserid owner :modifieruserid owner :title "with review" :fnlround 1}))
        two-round (:id (db/create-workflow! {:organization "nbn" :owneruserid owner :modifieruserid owner :title "two rounds" :fnlround 1}))
        different (:id (db/create-workflow! {:organization "nbn" :owneruserid owner :modifieruserid owner :title "two rounds, different approvers" :fnlround 1}))
        expired (:id (db/create-workflow! {:organization "nbn" :owneruserid owner :modifieruserid owner :title "workflow has already expired, should not be seen" :fnlround 0 :endt (time/minus (time/now) (time/years 1))}))
        dynamic (binding [context/*user* {"eppn" "owner"}]
                  (:id (workflow/create-workflow! {:organization "nbn" :title "dynamic workflow"
                                                   :type :dynamic :handlers [approver1]})))]
    ;; either approver1 or approver2 can approve
    (actors/add-approver! simple approver1 0)
    (actors/add-approver! simple approver2 0)
    ;; first reviewer reviews, then approver1 can approve
    (actors/add-reviewer! with-review reviewer 0)
    (actors/add-approver! with-review approver1 1)
    ;; only approver1 can approve
    (actors/add-approver! two-round approver1 0)
    (actors/add-approver! two-round approver1 1)
    ;; first approver2, then approver1
    (actors/add-approver! different approver2 0)
    (actors/add-approver! different approver1 1)

    ;; attach both kinds of licenses to all workflows
    (let [link (:id (db/create-license!
                     {:modifieruserid owner :owneruserid owner :title "non-localized link license"
                      :type "link" :textcontent "http://invalid"}))
          text (:id (db/create-license!
                     {:modifieruserid owner :owneruserid owner :title "non-localized text license"
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
     :different different
     :expired expired
     :dynamic dynamic}))

(defn- create-resource-license! [resid text owner]
  (let [licid (:id (db/create-license!
                    {:modifieruserid owner :owneruserid owner :title "resource license"
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
  (binding [context/*tempura* (locales/tempura-config)
            context/*user* {"eppn" applicant}]
    (create-draft! catid wfid "draft application")
    (let [application (create-draft! catid wfid "applied application")]
      (applications/submit-application applicant application))
    (let [application (create-draft! catid wfid "rejected application")]
      (applications/submit-application applicant application)
      (binding [context/*user* {"eppn" approver}]
        (applications/reject-application application 0 "comment for rejection")))
    (let [application (create-draft! catid wfid "accepted application")]
      (applications/submit-application applicant application)
      (binding [context/*user* {"eppn" approver}]
        (applications/approve-application application 0 "comment for approval")))
    (let [application (create-draft! catid wfid "returned application")]
      (applications/submit-application applicant application)
      (binding [context/*user* {"eppn" approver}]
        (applications/return-application application 0 "comment for return")))))

(defn- create-disabled-applications! [catid wfid applicant approver]
  (binding [context/*tempura* (locales/tempura-config)
            context/*user* {"eppn" applicant}]
    (create-draft! catid wfid "draft with disabled item")
    (let [application (create-draft! catid wfid "approved application with disabled item")]
      (applications/submit-application applicant application)
      (binding [context/*user* {"eppn" approver}]
        (applications/approve-application application 0 "comment for approval")))))

(defn- create-bundled-application! [catid catid2 wfid applicant approver]
  (binding [context/*tempura* (locales/tempura-config)
            context/*user* {"eppn" applicant}]
    (let [app-id (create-draft! [catid catid2] wfid "bundled application")]
      (applications/submit-application applicant app-id)
      (binding [context/*user* {"eppn" approver}]
        (applications/return-application app-id 0 "comment for return"))
      (applications/submit-application applicant app-id))))

(defn- create-dynamic-application! [catid wfid applicant]
  (let [app-id (applications/create-new-draft wfid applicant)]
    (db/add-application-item! {:application app-id :item catid})
    (applications/dynamic-command! {:type :rems.workflow.dynamic/submit
                                    :actor applicant
                                    :application-id app-id})
    app-id))

(defn- create-review-application! [catid wfid users]
  (let [applicant (users :applicant1)
        approver (users :approver1)
        reviewer (users :reviewer)]
    (binding [context/*tempura* (locales/tempura-config)
              context/*user* {"eppn" applicant}]
      (let [app-id (create-draft! catid wfid "application with review")]
        (applications/submit-application applicant app-id)
        (binding [context/*user* {"eppn" reviewer}]
          (applications/review-application app-id 0 "comment for review"))
        (binding [context/*user* {"eppn" approver}]
          (applications/approve-application app-id 1 "comment for approval"))))))

(defn- create-application-with-expired-resource-license! [wfid form users]
  (let [applicant (users :applicant1)
        owner (users :owner)
        resource-id (:id (db/create-resource! {:resid "Resource that has expired license" :organization "nbn" :owneruserid owner :modifieruserid owner}))
        year-ago (time/minus (time/now) (time/years 1))
        yesterday (time/minus (time/now) (time/days 1))
        licid-expired (create-resource-license! resource-id "License that has expired" owner)
        _ (db/set-resource-license-validity! {:licid licid-expired :start year-ago :end yesterday})
        item-with-expired-license (create-catalogue-item! resource-id wfid form {"en" "Resource with expired resource license"
                                                                                 "fi" "Resurssi jolla on vanhentunut resurssilisenssi"})]
    (binding [context/*tempura* (locales/tempura-config)
              context/*user* {"eppn" applicant}]
      (let [application (create-draft! item-with-expired-license wfid "applied when license was valid that has since expired" (time/minus (time/now) (time/days 2)))]
        (applications/submit-application applicant application)))))

(defn- create-application-before-new-resource-license! [wfid form users]
  (let [applicant (users :applicant1)
        owner (users :owner)
        resource-id (:id (db/create-resource! {:resid "Resource that has a new resource license" :organization "nbn" :owneruserid owner :modifieruserid owner}))
        licid-new (create-resource-license! resource-id "License that was just created" owner)
        _ (db/set-resource-license-validity! {:licid licid-new :start (time/now) :end nil})
        item-without-new-license (create-catalogue-item! resource-id wfid form {"en" "Resource with just created new resource license"
                                                                                "fi" "Resurssi jolla on uusi resurssilisenssi"})]
    (binding [context/*tempura* (locales/tempura-config)
              context/*user* {"eppn" applicant}]
      (let [application (create-draft! item-without-new-license wfid "applied before license was valid" (time/minus (time/now) (time/days 2)))]
        (applications/submit-application applicant application)))))

(defn create-test-data! []
  (db/add-api-key! {:apikey 42 :comment "test data"})
  (create-users-and-roles!)
  (let [res1 (:id (db/create-resource! {:resid "urn:nbn:fi:lb-201403262" :organization "nbn" :owneruserid (+fake-users+ :owner) :modifieruserid (+fake-users+ :owner)}))
        res2 (:id (db/create-resource! {:resid "Extra Data" :organization "nbn" :owneruserid (+fake-users+ :owner) :modifieruserid (+fake-users+ :owner)}))
        _ (:id (db/create-resource! {:resid "Expired Resource, should not be seen" :organization "nbn" :owneruserid (+fake-users+ :owner) :modifieruserid (+fake-users+ :owner) :endt (time/minus (time/now) (time/years 1))}))
        form (create-basic-form! +fake-users+)
        _ (create-expired-form!)
        workflows (create-workflows! +fake-users+)
        _ (create-catalogue-item! res1 (:minimal workflows) form
                                  {"en" "ELFA Corpus, direct approval"
                                   "fi" "ELFA-korpus, suora hyväksyntä"})
        simple (create-catalogue-item! res1 (:simple workflows) form
                                       {"en" "ELFA Corpus, one approval"
                                        "fi" "ELFA-korpus, yksi hyväksyntä"})
        bundlable (create-catalogue-item! res2 (:simple workflows) form
                                         {"en" "ELFA Corpus, one approval (extra data)"
                                          "fi" "ELFA-korpus, yksi hyväksyntä (lisäpaketti)"})
        with-review (create-catalogue-item! res1 (:with-review workflows) form
                                            {"en" "ELFA Corpus, with review"
                                             "fi" "ELFA-korpus, katselmoinnilla"})
        _ (create-catalogue-item! res1 (:different workflows) form
                                  {"en" "ELFA Corpus, two rounds of approval by different approvers"
                                   "fi" "ELFA-korpus, kaksi hyväksyntäkierrosta eri hyväksyjillä"})
        disabled (create-catalogue-item! res1 (:simple workflows) form
                                         {"en" "ELFA Corpus, one approval (extra data, disabled)"
                                          "fi" "ELFA-korpus, yksi hyväksyntä (lisäpaketti, pois käytöstä)"})]
    (create-resource-license! res2 "Some test license" (+fake-users+ :owner))
    (db/set-catalogue-item-state! {:item disabled :state "disabled" :user (+fake-users+ :approver1)})
    (create-applications! simple (:simple workflows) (+fake-users+ :approver1) (+fake-users+ :approver1))
    (create-disabled-applications! disabled (:simple workflows) (+fake-users+ :approver1) (+fake-users+ :approver1))
    (create-bundled-application! simple bundlable (:simple workflows) (+fake-users+ :applicant1) (+fake-users+ :approver1))
    (create-review-application! with-review (:with-review workflows) +fake-users+)
    (create-application-with-expired-resource-license! (:simple workflows) form +fake-users+)
    (create-application-before-new-resource-license!  (:simple workflows) form +fake-users+)
    (create-expired-license!)
    (let [dynamic (create-catalogue-item! res1 (:dynamic workflows) form
                                          {"en" "Dynamic workflow" "fi" "Dynaaminen työvuo"})]
      (create-dynamic-application! dynamic (:dynamic workflows) (+fake-users+ :applicant1)))))

(defn create-demo-data! []
  (create-demo-users-and-roles!)
  (let [res1 (:id (db/create-resource! {:resid "urn:nbn:fi:lb-201403262" :organization "nbn" :owneruserid (+demo-users+ :owner) :modifieruserid (+demo-users+ :owner)}))
        res2 (:id (db/create-resource! {:resid "Extra Data" :organization "nbn" :owneruserid (+demo-users+ :owner) :modifieruserid (+demo-users+ :owner)}))
        form (create-basic-form! +demo-users+)
        workflows (create-workflows! +demo-users+)
        _ (create-catalogue-item! res1 (:minimal workflows) form
                                  {"en" "ELFA Corpus, direct approval"
                                   "fi" "ELFA-korpus, suora hyväksyntä"})
        simple (create-catalogue-item! res1 (:simple workflows) form
                                       {"en" "ELFA Corpus, one approval"
                                        "fi" "ELFA-korpus, yksi hyväksyntä"})
        bundlable (create-catalogue-item! res2 (:simple workflows) form
                                         {"en" "ELFA Corpus, one approval (extra data)"
                                          "fi" "ELFA-korpus, yksi hyväksyntä (lisäpaketti)"})
        with-review (create-catalogue-item! res1 (:with-review workflows) form
                                            {"en" "ELFA Corpus, with review"
                                             "fi" "ELFA-korpus, katselmoinnilla"})
        _ (create-catalogue-item! res1 (:different workflows) form
                                  {"en" "ELFA Corpus, two rounds of approval by different approvers"
                                   "fi" "ELFA-korpus, kaksi hyväksyntäkierrosta eri hyväksyjillä"})
        disabled (create-catalogue-item! res1 (:simple workflows) form
                                         {"en" "ELFA Corpus, one approval (extra data, disabled)"
                                          "fi" "ELFA-korpus, yksi hyväksyntä (lisäpaketti, pois käytöstä)"})]
    (create-resource-license! res2 "Some demo license" (+demo-users+ :owner))
    (db/set-catalogue-item-state! {:item disabled :state "disabled" :user (+demo-users+ :owner)})
    (create-applications! simple (:simple workflows) (+demo-users+ :applicant1) (+demo-users+ :approver1))
    (create-disabled-applications! disabled (:simple workflows) (+demo-users+ :applicant1) (+demo-users+ :approver1))
    (create-bundled-application! simple bundlable (:simple workflows) (+demo-users+ :applicant2) (+demo-users+ :approver1))
    (create-review-application! with-review (:with-review workflows) +demo-users+)
    (create-application-with-expired-resource-license! (:simple workflows) form +demo-users+)
    (create-application-before-new-resource-license!  (:simple workflows) form +demo-users+)
    (create-expired-license!)
    (let [dynamic (create-catalogue-item! res1 (:dynamic workflows) form
                                          {"en" "Dynamic workflow" "fi" "Dynaaminen työvuo"})]
      (create-dynamic-application! dynamic (:dynamic workflows) (+demo-users+ :applicant1)))))

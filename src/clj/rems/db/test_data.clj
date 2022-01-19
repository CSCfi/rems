(ns rems.db.test-data
  "Populating the database with nice test data."
  (:require [clj-time.core :as time]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [rems.api.schema :as schema]
            [rems.api.services.form :as form]
            [rems.api.services.organizations :as organizations]
            [rems.db.api-key :as api-key]
            [rems.db.core :as db]
            [rems.db.roles :as roles]
            [rems.db.users :as users]
            [rems.db.test-data-helpers :refer :all]
            [rems.db.test-data-users :refer :all]
            [rems.testing-util :refer [with-user]]
            [rems.config])
  (:import [java.util UUID]
           [java.util.concurrent Executors Future]))

(def +test-api-key+ "42")

;;; generate test data

(defn- create-users-and-roles! [users attrs]
  (doseq [attr (vals attrs)]
    (create-user! attr))
  (roles/add-role! (users :owner) :owner)
  (roles/add-role! (users :reporter) :reporter))

(defn create-test-users-and-roles! []
  ;; users provided by the fake login
  (create-users-and-roles! +fake-users+ +fake-user-data+)
  ;; invalid user for tests
  (db/add-user! {:user "invalid" :userattrs nil}))

(defn create-bots! []
  (doseq [attr (vals +bot-user-data+)]
    (create-user! attr)))

(defn- create-archived-form! [actor]
  (with-user actor
    (let [id (create-form! {:actor actor
                            :organization {:organization/id "nbn"}
                            :form/internal-name "Archived form, should not be seen by applicants"
                            :form/external-title {:en "Archived form, should not be seen by applicants"
                                                  :fi "Archived form, should not be seen by applicants"
                                                  :sv "Archived form, should not be seen by applicants"}})]
      (form/set-form-archived! {:id id :archived true}))))

(defn- create-disabled-license! [{:keys [actor organization]}]
  (let [id (create-license! {:actor actor
                             :license/type "link"
                             :organization organization
                             :license/title {:en "Disabled license"
                                             :fi "Käytöstä poistettu lisenssi"}
                             :license/link {:en "http://disabled"
                                            :fi "http://disabled"}})]
    (db/set-license-enabled! {:id id :enabled false})))

(def all-field-types-example
  [{:field/type :label
    :field/title {:en "This form demonstrates all possible field types. (This text itself is a label field.)"
                  :fi "Tämä lomake havainnollistaa kaikkia mahdollisia kenttätyyppejä. (Tämä teksti itsessään on lisätietokenttä.)"
                  :sv "Detta blanket visar alla möjliga fälttyper. (Det här texten är en fält för tilläggsinformation.)"}
    :field/optional false}

   {:field/type :description
    :field/title {:en "Application title field"
                  :fi "Hakemuksen otsikko -kenttä"
                  :sv "Ansökningens rubrikfält"}
    :field/optional false}

   {:field/type :text
    :field/title {:en "Text field"
                  :fi "Tekstikenttä"
                  :sv "Textfält"}
    :field/optional false
    :field/info-text {:en "Explanation of how to fill in text field"
                      :fi "Selitys tekstikentän täyttämisestä"
                      :sv "Förklaring till hur man fyller i textfält"}
    :field/placeholder {:en "Placeholder text"
                        :fi "Täyteteksti"
                        :sv "Textexempel"}}

   {:field/type :texta
    :field/title {:en "Text area"
                  :fi "Tekstialue"
                  :sv "Textområde"}
    :field/optional false
    :field/placeholder {:en "Placeholder text"
                        :fi "Täyteteksti"
                        :sv "Textexempel"}}

   {:field/type :header
    :field/title {:en "Header"
                  :fi "Otsikko"
                  :sv "Titel"}
    :field/optional false}

   {:field/type :date
    :field/title {:en "Date field"
                  :fi "Päivämääräkenttä"
                  :sv "Datumfält"}
    :field/optional true}

   {:field/type :email
    :field/title {:en "Email field"
                  :fi "Sähköpostikenttä"
                  :sv "E-postaddressfält"}
    :field/optional true}

   {:field/type :attachment
    :field/title {:en "Attachment"
                  :fi "Liitetiedosto"
                  :sv "Bilaga"}
    :field/optional true}

   {:field/type :option
    :field/title {:en "Option list. Choose the first option to reveal a new field."
                  :fi "Valintalista. Valitse ensimmäinen vaihtoehto paljastaaksesi uuden kentän."
                  :sv "Lista. Välj det första alternativet för att visa ett nytt fält."}
    :field/optional true
    :field/id "option"
    :field/options [{:key "Option1"
                     :label {:en "First option"
                             :fi "Ensimmäinen vaihtoehto"
                             :sv "Första alternativ"}}
                    {:key "Option2"
                     :label {:en "Second option"
                             :fi "Toinen vaihtoehto"
                             :sv "Andra alternativ"}}
                    {:key "Option3"
                     :label {:en "Third option"
                             :fi "Kolmas vaihtoehto"
                             :sv "Tredje alternativ"}}]}

   {:field/type :text
    :field/title {:en "Conditional field. Shown only if first option is selected above."
                  :fi "Ehdollinen kenttä. Näytetään vain jos yllä valitaan ensimmäinen vaihtoehto."
                  :sv "Villkorlig fält. Visas bara som första alternativet har väljats ovan."}
    :field/optional false
    :field/visibility {:visibility/type :only-if
                       :visibility/field {:field/id "option"}
                       :visibility/values ["Option1"]}}

   {:field/type :multiselect
    :field/title {:en "Multi-select list"
                  :fi "Monivalintalista"
                  :sv "Lista med flerval"}
    :field/optional true
    :field/options [{:key "Option1"
                     :label {:en "First option"
                             :fi "Ensimmäinen vaihtoehto"
                             :sv "Första alternativ"}}
                    {:key "Option2"
                     :label {:en "Second option"
                             :fi "Toinen vaihtoehto"
                             :sv "Andra alternativ"}}
                    {:key "Option3"
                     :label {:en "Third option"
                             :fi "Kolmas vaihtoehto"
                             :sv "Tredje alternativ"}}]}

   {:field/type :table
    :field/title {:en "Table"
                  :fi "Taulukko"
                  :sv "Tabell"}
    :field/optional true
    :field/columns [{:key "col1"
                     :label {:en "First"
                             :fi "Ensimmäinen"
                             :sv "Första"}}
                    {:key "col2"
                     :label {:en "Second"
                             :fi "Toinen"
                             :sv "Andra"}}]}

   {:field/type :label
    :field/title {:en "The following field types can have a max length."
                  :fi "Seuraavilla kenttätyypeillä voi olla pituusrajoitus."
                  :sv "De nästa fälttyperna kan ha bengränsat längd."}
    :field/optional false}

   ;; fields which support maxlength
   {:field/type :text
    :field/title {:en "Text field with max length"
                  :fi "Tekstikenttä pituusrajalla"
                  :sv "Textfält med begränsat längd"}
    :field/optional true
    :field/max-length 10}

   {:field/type :texta
    :field/title {:en "Text area with max length"
                  :fi "Tekstialue pituusrajalla"
                  :sv "Textområdet med begränsat längd"}
    :field/optional true
    :field/max-length 100}

   {:field/type :phone-number
    :field/title {:en "Phone number"
                  :fi "Puhelinnumero"
                  :sv "Telefonnummer"}
    :field/optional true}

   {:field/type :ip-address
    :field/title {:en "IP address"
                  :fi "IP-osoite"
                  :sv "IP-adress"}
    :field/optional true}])

(deftest test-all-field-types-example
  (is (= (:vs (:field/type schema/FieldTemplate))
         (set (map :field/type all-field-types-example)))
      "a new field has been added to schema but not to this test data"))

(defn create-all-field-types-example-form!
  "Creates a bilingual form with all supported field types. Returns the form ID."
  [actor organization internal-name external-title]
  (create-form!
   {:actor actor
    :organization organization
    :form/internal-name internal-name
    :form/external-title external-title
    :form/fields all-field-types-example}))

(defn- create-workflows! [users]
  (let [approver1 (users :approver1)
        approver2 (users :approver2)
        approver-bot (users :approver-bot)
        rejecter-bot (users :rejecter-bot)
        owner (users :owner)
        organization-owner1 (users :organization-owner1)
        handlers [approver1 approver2 rejecter-bot]
        default (create-workflow! {:actor owner
                                   :organization {:organization/id "nbn"}
                                   :title "Default workflow"
                                   :type :workflow/default
                                   :handlers handlers})
        decider (create-workflow! {:actor owner
                                   :organization {:organization/id "nbn"}
                                   :title "Decider workflow"
                                   :type :workflow/decider
                                   :handlers handlers})
        master (create-workflow! {:actor owner
                                  :organization {:organization/id "nbn"}
                                  :title "Master workflow"
                                  :type :workflow/master
                                  :handlers handlers})
        auto-approve (create-workflow! {:actor owner
                                        :organization {:organization/id "nbn"}
                                        :title "Auto-approve workflow"
                                        :handlers [approver-bot rejecter-bot]})
        organization-owner (create-workflow! {:actor organization-owner1
                                              :organization {:organization/id "organization1"}
                                              :title "Owned by organization owner"
                                              :type :workflow/default
                                              :handlers handlers})
        with-form (create-workflow! {:actor owner
                                     :organization {:organization/id "nbn"}
                                     :title "With workflow form"
                                     :type :workflow/default
                                     :handlers handlers
                                     :forms [{:form/id (create-form! {:actor owner
                                                                      :form/internal-name "Workflow form"
                                                                      :form/external-title {:en "Workflow form"
                                                                                            :fi "Työvuon lomake"
                                                                                            :sv "Blankett för arbetsflöde"}
                                                                      :organization {:organization/id "nbn"}
                                                                      :form/fields [{:field/type :description
                                                                                     :field/title {:fi "Kuvaus"
                                                                                                   :en "Description"
                                                                                                   :sv "Rubrik"}
                                                                                     :field/optional false}]})}]})
        ega (create-workflow! {:actor owner
                               :organization {:organization/id "csc"}
                               :title "EGA workflow, a variant of default"
                               :type :workflow/default
                               :handlers handlers})]

    ;; attach both kinds of licenses to all workflows created by owner except EGA
    (let [link (create-license! {:actor owner
                                 :license/type :link
                                 :organization {:organization/id "nbn"}
                                 :license/title {:en "CC Attribution 4.0"
                                                 :fi "CC Nimeä 4.0"
                                                 :sv "CC Erkännande 4.0"}
                                 :license/link {:en "https://creativecommons.org/licenses/by/4.0/legalcode"
                                                :fi "https://creativecommons.org/licenses/by/4.0/legalcode.fi"
                                                :sv "https://creativecommons.org/licenses/by/4.0/legalcode.sv"}})
          text (create-license! {:actor owner
                                 :license/type :text
                                 :organization {:organization/id "nbn"}
                                 :license/title {:en "General Terms of Use"
                                                 :fi "Yleiset käyttöehdot"
                                                 :sv "Allmänna villkor"}
                                 :license/text {:en (apply str (repeat 10 "License text in English. "))
                                                :fi (apply str (repeat 10 "Suomenkielinen lisenssiteksti. "))
                                                :sv (apply str (repeat 10 "Licens på svenska. "))}})]
      (doseq [licid [link text]]
        (doseq [wfid [default decider master auto-approve with-form]]
          (db/create-workflow-license! {:wfid wfid :licid licid}))))

    {:default default
     :ega ega
     :decider decider
     :master master
     :auto-approve auto-approve
     :organization-owner organization-owner}))

(defn- create-bona-fide-catalogue-item! [users]
  (let [owner (:owner users)
        bot (:bona-fide-bot users)
        res (create-resource! {:resource-ext-id "bona-fide"
                               :organization {:organization/id "default"}
                               :actor owner})
        form (create-form! {:actor owner
                            :form/internal-name "Bona Fide form"
                            :form/external-title {:en "Form"
                                                  :fi "Lomake"
                                                  :sv "Blankett"}
                            :organization {:organization/id "default"}
                            :form/fields [{:field/type :email
                                           :field/title {:fi "Suosittelijan sähköpostiosoite"
                                                         :en "Referer's email address"
                                                         :sv "sv"}
                                           :field/optional false}]})
        wf (create-workflow! {:actor owner
                              :organization {:organization/id "default"}
                              :title "Bona Fide workflow"
                              :type :workflow/default
                              :handlers [bot]})]
    (create-catalogue-item! {:actor owner
                             :organization {:organization/id "default"}
                             :title {:en "Apply for Bona Fide researcher status"
                                     :fi "Hae Bona Fide tutkija -statusta"
                                     :sv "sv"}
                             :resource-id res
                             :form-id form
                             :workflow-id wf})))

(defn- create-disabled-applications! [catid applicant approver]
  (create-draft! applicant [catid] "draft with disabled item")

  (let [appid1 (create-draft! applicant [catid] "submitted application with disabled item")]
    (command! {:type :application.command/submit
               :application-id appid1
               :actor applicant}))

  (let [appid2 (create-draft! applicant [catid] "approved application with disabled item")]
    (command! {:type :application.command/submit
               :application-id appid2
               :actor applicant})
    (command! {:type :application.command/approve
               :application-id appid2
               :actor approver
               :comment "Looking good"})))

(defn- create-member-applications! [catid applicant approver members]
  (let [appid1 (create-draft! applicant [catid] "draft with invited members")]
    (command! {:type :application.command/invite-member
               :application-id appid1
               :actor applicant
               :member {:name "John Smith" :email "john.smith@example.org"}}))
  (let [appid2 (create-draft! applicant [catid] "submitted with members")]
    (command! {:type :application.command/invite-member
               :application-id appid2
               :actor applicant
               :member {:name "John Smith" :email "john.smith@example.org"}})
    (command! {:type :application.command/submit
               :application-id appid2
               :actor applicant})
    (doseq [member members]
      (command! {:type :application.command/add-member
                 :application-id appid2
                 :actor approver
                 :member member}))))

(defn- create-applications! [catid users]
  (let [applicant (users :applicant1)
        approver (users :approver1)
        reviewer (users :reviewer)]

    (create-draft! applicant [catid] "draft application")

    (let [app-id (create-draft! applicant [catid] "applied")]
      (command! {:type :application.command/submit
                 :application-id app-id
                 :actor applicant}))

    (let [time (time/minus (time/now) (time/days 7))
          app-id (create-draft! applicant [catid] "old applied" time)]
      (command! {:time time
                 :type :application.command/submit
                 :application-id app-id
                 :actor applicant}))

    (let [app-id (create-draft! applicant [catid] "approved with comment")]
      (command! {:type :application.command/submit
                 :application-id app-id
                 :actor applicant})
      (command! {:type :application.command/request-review
                 :application-id app-id
                 :actor approver
                 :reviewers [reviewer]
                 :comment "please have a look"})
      (command! {:type :application.command/review
                 :application-id app-id
                 :actor reviewer
                 :comment "looking good"})
      (command! {:type :application.command/approve
                 :application-id app-id
                 :actor approver
                 :comment "Thank you! Approved!"}))

    (let [app-id (create-draft! applicant [catid] "rejected")]
      (command! {:type :application.command/submit
                 :application-id app-id
                 :actor applicant})
      (command! {:type :application.command/reject
                 :application-id app-id
                 :actor approver
                 :comment "Never going to happen"}))

    (let [app-id (create-draft! applicant [catid] "returned")]
      (command! {:type :application.command/submit
                 :application-id app-id
                 :actor applicant})
      (command! {:type :application.command/return
                 :application-id app-id
                 :actor approver
                 :comment "Need more details"}))

    (let [app-id (create-draft! applicant [catid] "approved & closed")]
      (command! {:type :application.command/submit
                 :application-id app-id
                 :actor applicant})
      (command! {:type :application.command/request-review
                 :application-id app-id
                 :actor approver
                 :reviewers [reviewer]
                 :comment "please have a look"})
      (command! {:type :application.command/review
                 :application-id app-id
                 :actor reviewer
                 :comment "looking good"})
      (command! {:type :application.command/approve
                 :application-id app-id
                 :actor approver
                 :comment "Thank you! Approved!"})
      (command! {:type :application.command/close
                 :application-id app-id
                 :actor approver
                 :comment "Research project complete, closing."}))

    (let [app-id (create-draft! applicant [catid] "waiting for review")]
      (command! {:type :application.command/submit
                 :application-id app-id
                 :actor applicant})
      (command! {:type :application.command/request-review
                 :application-id app-id
                 :actor approver
                 :reviewers [reviewer]
                 :comment ""}))

    (let [app-id (create-draft! applicant [catid] "waiting for decision")]
      (command! {:type :application.command/submit
                 :application-id app-id
                 :actor applicant})
      (command! {:type :application.command/request-decision
                 :application-id app-id
                 :actor approver
                 :deciders [reviewer]
                 :comment ""}))))

(defn- range-1
  "Like `clojure.core/range`, but starts from 1 and `end` is inclusive."
  [end]
  (range 1 (inc end)))

(defn- in-parallel [fs]
  (let [executor (Executors/newFixedThreadPool 10)]
    (try
      (->> fs
           (.invokeAll executor)
           (map #(.get ^Future %))
           doall)
      (finally
        (.shutdownNow executor)))))

(def ^:private vocabulary (-> "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Ut non diam vel erat dapibus facilisis vel vitae nunc. Curabitur at fermentum lorem. Cras et bibendum ante. Etiam convallis erat justo. Phasellus cursus molestie vehicula. Etiam molestie tellus vitae consectetur dignissim. Pellentesque euismod hendrerit mi sed tincidunt. Integer quis lorem ut ipsum egestas hendrerit. Aenean est nunc, mattis euismod erat in, sodales rutrum mauris. Praesent sit amet risus quis felis congue ultricies. Nulla facilisi. Sed mollis justo id tristique volutpat.\n\nPhasellus augue mi, facilisis ac velit et, pharetra tristique nunc. Pellentesque eget arcu quam. Curabitur dictum nulla varius hendrerit varius. Proin vulputate, ex lacinia commodo varius, ipsum velit viverra est, eget molestie dui nisi non eros. Nulla lobortis odio a magna mollis placerat. Interdum et malesuada fames ac ante ipsum primis in faucibus. Integer consectetur libero ut gravida ullamcorper. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas. Donec aliquam feugiat mollis. Quisque massa lacus, efficitur vel justo vel, elementum mollis magna. Maecenas at sem sem. Praesent sed ex mattis, egestas dui non, volutpat lorem. Nulla tempor, nisi rutrum accumsan varius, tellus elit faucibus nulla, vel mattis lacus justo at ante. Sed ut mollis ex, sed tincidunt ex.\n\nMauris laoreet nibh eget erat tincidunt pharetra. Aenean sagittis maximus consectetur. Curabitur interdum nibh sed tincidunt finibus. Sed blandit nec lorem at iaculis. Morbi non augue nec tortor hendrerit mollis ut non arcu. Suspendisse maximus nec ligula a efficitur. Etiam ultrices rhoncus leo quis dapibus. Integer vel rhoncus est. Integer blandit varius auctor. Vestibulum suscipit suscipit risus, sit amet venenatis lacus iaculis a. Duis eu turpis sit amet nibh sagittis convallis at quis ligula. Sed eget justo quis risus iaculis lacinia vitae a justo. In hac habitasse platea dictumst. Maecenas euismod et lorem vel viverra.\n\nDonec bibendum nec ipsum in volutpat. Vivamus in elit venenatis, venenatis libero ac, ultrices dolor. Morbi quis odio in neque consequat rutrum. Suspendisse quis sapien id sapien fermentum dignissim. Nam eu est vel risus volutpat mollis sed quis eros. Proin leo nulla, dictum id hendrerit vitae, scelerisque in elit. Proin consectetur sodales arcu ac tristique. Suspendisse ut elementum ligula, at rhoncus mauris. Aliquam lacinia at diam eget mattis. Phasellus quam leo, hendrerit sit amet mi eget, porttitor aliquet velit. Proin turpis ante, consequat in enim nec, tempus consequat magna. Vestibulum fringilla ac turpis nec malesuada. Proin id lectus iaculis, suscipit erat at, volutpat turpis. In quis faucibus elit, ut maximus nibh. Sed egestas egestas dolor.\n\nNulla varius orci quam, id auctor enim ultrices nec. Morbi et tellus ac metus sodales convallis sed vehicula neque. Pellentesque rhoncus mattis massa a bibendum. Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia Curae; Fusce tincidunt nulla non aliquet facilisis. Praesent nisl nisi, finibus id odio sed, consectetur feugiat mauris. Suspendisse sed lacinia ligula. Duis vitae nisl leo. Donec erat arcu, feugiat sit amet sagittis ac, scelerisque nec est. Pellentesque finibus mauris nulla, in maximus sapien pharetra vitae. Sed leo elit, consequat eu aliquam vitae, feugiat ut eros. Pellentesque dictum feugiat odio sed commodo. Lorem ipsum dolor sit amet, consectetur adipiscing elit. Proin neque quam, varius vel libero sit amet, rhoncus sollicitudin ex. In a dui non neque malesuada pellentesque.\n\nProin tincidunt nisl non commodo faucibus. Sed porttitor arcu neque, vitae bibendum sapien placerat nec. Integer eget tristique orci. Class aptent taciti sociosqu ad litora torquent per conubia nostra, per inceptos himenaeos. Donec eu molestie eros. Nunc iaculis rhoncus enim, vel mattis felis fringilla condimentum. Interdum et malesuada fames ac ante ipsum primis in faucibus. Aenean ac augue nulla. Phasellus vitae nulla lobortis, mattis magna ac, gravida ipsum. Aenean ornare non nunc non luctus. Aenean lacinia lectus nec velit finibus egestas vel ut ipsum. Cras hendrerit rhoncus erat, vel maximus nunc.\n\nPraesent quis imperdiet quam. Praesent ligula tellus, consectetur sed lacus eu, malesuada condimentum tellus. Donec et diam hendrerit, dictum diam quis, aliquet purus. Suspendisse pulvinar neque at efficitur iaculis. Nulla erat orci, euismod id velit sed, dictum hendrerit arcu. Nulla aliquam molestie aliquam. Duis et semper nisi, eget commodo arcu. Praesent rhoncus, nulla id sodales eleifend, ante ipsum pellentesque augue, id iaculis sem est vitae est. Phasellus cursus diam a lorem vestibulum sodales. Nullam lacinia tortor vel tellus commodo, sit amet sodales quam malesuada.\n\nNulla tempor lectus vel arcu feugiat, vel dapibus ex dapibus. Maecenas purus justo, aliquet et sem sit amet, tincidunt venenatis dui. Nulla eget purus id sapien elementum rutrum eu vel libero. Cras non accumsan justo posuere.\n\n"
                              str/lower-case
                              (str/split #"[( \n)+]")
                              distinct
                              sort
                              rest))

(defn- random-long-string []
  (str (str/join " " (repeatedly 1000 #(rand-nth vocabulary)))
       ;; prevent string interning, just to be sure
       (UUID/randomUUID)))

(defn create-performance-test-data! []
  (log/info "Creating performance test data")
  (let [resource-count 1000
        application-count 1000
        user-count 1000
        handlers [(+fake-users+ :approver1)
                  (+fake-users+ :approver2)]
        owner (+fake-users+ :owner)
        _perf (organizations/add-organization! owner {:organization/id "perf"
                                                      :organization/name {:fi "Suorituskykytestiorganisaatio" :en "Performance Test Organization" :sv "Organisationen för utvärderingsprov"}
                                                      :organization/short-name {:fi "Suorituskyky" :en "Performance" :sv "Uvärderingsprov"}
                                                      :organization/owners [{:userid (+fake-users+ :organization-owner1)}]
                                                      :organization/review-emails []})
        workflow-id (create-workflow! {:actor owner
                                       :organization {:organization/id "perf"}
                                       :title "Performance tests"
                                       :handlers handlers})
        form-id (create-form!
                 {:actor owner
                  :organization {:organization/id "perf"}
                  :form/internal-name "Performance tests"
                  :form/external-title {:en "Performance tests EN"
                                        :fi "Performance tests FI"
                                        :sv "Performance tests SV"}
                  :form/fields [{:field/title {:en "Project name"
                                               :fi "Projektin nimi"
                                               :sv "Projektets namn"}
                                 :field/optional false
                                 :field/type :description
                                 :field/placeholder {:en "Project"
                                                     :fi "Projekti"
                                                     :sv "Projekt"}}

                                {:field/title {:en "Project description"
                                               :fi "Projektin kuvaus"
                                               :sv "Projektets beskrivning"}
                                 :field/optional false
                                 :field/type :texta
                                 :field/placeholder {:en "The purpose of the project is to..."
                                                     :fi "Projektin tarkoitus on..."
                                                     :sv "Det här projekt..."}}]})
        form (form/get-form-template form-id)
        category {:category/id (create-category! {:actor owner
                                                  :category/title {:en "Performance"
                                                                   :fi "Suorituskyky"
                                                                   :sv "Prestand"}
                                                  :category/description {:en "These catalogue items are for performance test."
                                                                         :fi "Nämä resurssit ovat suorituskykytestausta varten."
                                                                         :sv "Dessa resurser är för prestand."}})}
        license-id (create-license! {:actor owner
                                     :license/type :text
                                     :organization {:organization/id "perf"}
                                     :license/title {:en "Performance License"
                                                     :fi "Suorituskykylisenssi"
                                                     :sv "Licens för prestand"}
                                     :license/text {:en "Be fast."
                                                    :fi "Ole nopea."
                                                    :sv "Var snabb."}})
        cat-item-ids (vec (in-parallel
                           (for [n (range-1 resource-count)]
                             (fn []
                               (let [resource-id (create-resource! {:organization {:organization/id "perf"}
                                                                    :license-ids [license-id]})]
                                 (create-catalogue-item! {:actor owner
                                                          :title {:en (str "Performance test resource " n)
                                                                  :fi (str "Suorituskykytestiresurssi " n)
                                                                  :sv (str "Licens för prestand " n)}
                                                          :resource-id resource-id
                                                          :form-id form-id
                                                          :organization {:organization/id "perf"}
                                                          :workflow-id workflow-id
                                                          :categories [category]}))))))
        user-ids (vec (in-parallel
                       (for [n (range-1 user-count)]
                         (fn []
                           (let [user-id (str "perftester" n)]
                             (users/add-user-raw! user-id {:eppn user-id
                                                           :mail (str user-id "@example.com")
                                                           :commonName (str "Performance Tester " n)})
                             user-id)))))]
    (in-parallel
     (for [n (range-1 application-count)]
       (fn []
         (log/info "Creating performance test application" n "/" application-count)
         (let [cat-item-id (rand-nth cat-item-ids)
               user-id (rand-nth user-ids)
               handler (rand-nth handlers)
               app-id (create-application! {:catalogue-item-ids [cat-item-id]
                                            :actor user-id})
               long-answer (random-long-string)]
           (dotimes [i 20] ; user saves ~ 20 times while writing an application
             (command! {:type :application.command/save-draft
                        :application-id app-id
                        :actor user-id
                        :field-values [{:form form-id
                                        :field (:field/id (first (:form/fields form)))
                                        :value (str "Performance test application " (UUID/randomUUID))}
                                       {:form form-id
                                        :field (:field/id (second (:form/fields form)))
                                        ;; 1000 words of lorem ipsum samples from a text from www.lipsum.com
                                        ;; to increase the memory requirements of an application
                                        :value (subs long-answer 0 (int (/ (* (inc i) (count long-answer)) (inc i))))}]}))
           (command! {:type :application.command/accept-licenses
                      :application-id app-id
                      :actor user-id
                      :accepted-licenses [license-id]})
           (command! {:type :application.command/submit
                      :application-id app-id
                      :actor user-id})
           (command! {:type :application.command/approve
                      :application-id app-id
                      :actor handler
                      :comment ""})))))
    (log/info "Performance test applications created")))

(defn- create-items! [users]
  (let [owner (users :owner)
        organization-owner1 (users :organization-owner1)

        ;; Create licenses
        license1 (create-license! {:actor owner
                                   :license/type :link
                                   :organization {:organization/id "nbn"}
                                   :license/title {:en "Demo license"
                                                   :fi "Demolisenssi"
                                                   :sv "Demolicens"}
                                   :license/link {:en "https://www.apache.org/licenses/LICENSE-2.0"
                                                  :fi "https://www.apache.org/licenses/LICENSE-2.0"
                                                  :sv "https://www.apache.org/licenses/LICENSE-2.0"}})
        license2 (create-license! {:actor owner
                                   :license/type :link
                                   :organization {:organization/id "nbn"}
                                   :license/title {:en "Demo license 2"
                                                   :fi "Demolisenssi 2"
                                                   :sv "Demolicens 2"}
                                   :license/link {:en "https://fedoraproject.org/wiki/Licensing/Beerware"
                                                  :fi "https://fedoraproject.org/wiki/Licensing/Beerware"
                                                  :sv "https://fedoraproject.org/wiki/Licensing/Beerware"}})
        extra-license (create-license! {:actor owner
                                        :license/type :link
                                        :organization {:organization/id "nbn"}
                                        :license/title {:en "Extra license"
                                                        :fi "Ylimääräinen lisenssi"
                                                        :sv "Extra licens"}
                                        :license/link {:en "https://www.apache.org/licenses/LICENSE-2.0"
                                                       :fi "https://www.apache.org/licenses/LICENSE-2.0"
                                                       :sv "https://www.apache.org/licenses/LICENSE-2.0"}})
        license-organization-owner (create-license! {:actor organization-owner1
                                                     :license/type :link
                                                     :organization {:organization/id "organization1"}
                                                     :license/title {:en "License owned by organization owner"
                                                                     :fi "Lisenssi, jonka omistaa organisaatio-omistaja"
                                                                     :sv "Licens som ägs av organisationägare"}
                                                     :license/link {:en "https://www.apache.org/licenses/LICENSE-2.0"
                                                                    :fi "https://www.apache.org/licenses/LICENSE-2.0"
                                                                    :sv "https://www.apache.org/licenses/LICENSE-2.0"}})

        ega-creative-commons-license (create-license! {:actor owner
                                                       :license/type :link
                                                       :organization {:organization/id "csc"}
                                                       :license/title {:en "CC Attribution 4.0"
                                                                       :fi "CC Nimeä 4.0"
                                                                       :sv "CC Erkännande 4.0"}
                                                       :license/link {:en "https://creativecommons.org/licenses/by/4.0/legalcode"
                                                                      :fi "https://creativecommons.org/licenses/by/4.0/legalcode.fi"
                                                                      :sv "https://creativecommons.org/licenses/by/4.0/legalcode.sv"}})

        _ (create-disabled-license! {:actor owner
                                     :organization {:organization/id "nbn"}})
        attachment-license (create-attachment-license! {:actor owner
                                                        :organization {:organization/id "nbn"}})

        ;; Create resources
        res1 (create-resource! {:resource-ext-id "urn:nbn:fi:lb-201403262"
                                :organization {:organization/id "nbn"}
                                :actor owner})
        res2 (create-resource! {:resource-ext-id "Extra Data"
                                :organization {:organization/id "nbn"}
                                :actor owner
                                :license-ids [license1]})
        res3 (create-resource! {:resource-ext-id "something else"
                                :organization {:organization/id "hus"}
                                :actor owner
                                :license-ids [license1 extra-license attachment-license]})

        ega-resource (create-resource! {:resource-ext-id "EGAD00001006673"
                                        :organization {:organization/id "csc"}
                                        :actor owner
                                        :license-ids [ega-creative-commons-license]})

        res-organization-owner (create-resource! {:resource-ext-id "Owned by organization owner"
                                                  :organization {:organization/id "organization1"}
                                                  :actor organization-owner1
                                                  :license-ids [license-organization-owner]})
        res-with-extra-license (create-resource! {:resource-ext-id "urn:nbn:fi:lb-201403263"
                                                  :organization {:organization/id "nbn"}
                                                  :actor owner
                                                  :license-ids [extra-license attachment-license]})
        _res-duplicate-resource-name1 (create-resource! {:resource-ext-id "duplicate resource name"
                                                         :organization {:organization/id "hus"}
                                                         :actor owner
                                                         :license-ids [license1 extra-license attachment-license]})
        _res-duplicate-resource-name2 (create-resource! {:resource-ext-id "duplicate resource name"
                                                         :organization {:organization/id "hus"}
                                                         :actor owner
                                                         :license-ids [license2 extra-license attachment-license]})
        _res-duplicate-resource-name-with-long-name1 (create-resource! {:resource-ext-id "urn:nbn:fi:lb-201403263443773465837568375683683756"
                                                                        :organization {:organization/id "hus"}
                                                                        :actor owner
                                                                        :license-ids [license1 extra-license attachment-license]})
        _res-duplicate-resource-name-with-long-name2 (create-resource! {:resource-ext-id "urn:nbn:fi:lb-201403263443773465837568375683683756"
                                                                        :organization {:organization/id "hus"}
                                                                        :actor owner
                                                                        :license-ids [license2 extra-license attachment-license]})
        duo-resource (when (:enable-duo rems.config/env)
                       (create-resource! {:resource-ext-id "All DUO codes with restrictions"
                                          :organization {:organization/id "nbn"}
                                          :actor owner
                                          :resource/duo {:duo/codes [{:id "DUO:0000007" :restrictions [{:type :mondo
                                                                                                        :values [{:id "MONDO:0000015"}]}]}
                                                                     {:id "DUO:0000012" :restrictions [{:type :topic
                                                                                                        :values [{:value "my research type"}]}]}
                                                                     {:id "DUO:0000020" :restrictions [{:type :collaboration
                                                                                                        :values [{:value "developers"}]}]}
                                                                     {:id "DUO:0000022" :restrictions [{:type :location
                                                                                                        :values [{:value "egentliga finland"}]}]}
                                                                     {:id "DUO:0000024" :restrictions [{:type :date
                                                                                                        :values [{:value "2021-10-29"}]}]}
                                                                     {:id "DUO:0000025" :restrictions [{:type :months
                                                                                                        :values [{:value "120"}]}]}
                                                                     {:id "DUO:0000026" :restrictions [{:type :users
                                                                                                        :values [{:value "alice"}]}]}
                                                                     {:id "DUO:0000027" :restrictions [{:type :project
                                                                                                        :values [{:value "rems"}]}]}
                                                                     {:id "DUO:0000028" :restrictions [{:type :institute
                                                                                                        :values [{:value "csc"}]}]}]}}))

        workflows (create-workflows! (merge users +bot-users+))
        _ (db/create-workflow-license! {:wfid (:organization-owner workflows)
                                        :licid license-organization-owner})

        form (create-all-field-types-example-form! owner {:organization/id "nbn"} "Example form with all field types" {:en "Example form with all field types"
                                                                                                                       :fi "Esimerkkilomake kaikin kenttätyypein"
                                                                                                                       :sv "Exempelblankett med alla fälttyper"})

        form-with-public-and-private-fields (create-form! {:actor owner
                                                           :organization {:organization/id "nbn"}
                                                           :form/internal-name "Public and private fields form"
                                                           :form/external-title {:en "Form"
                                                                                 :fi "Lomake"
                                                                                 :sv "Blankett"}
                                                           :form/fields [{:field/title {:en "Simple text field"
                                                                                        :fi "Yksinkertainen tekstikenttä"
                                                                                        :sv "Textfält"}
                                                                          :field/optional false
                                                                          :field/type :text
                                                                          :field/max-length 100}
                                                                         {:field/title {:en "Private text field"
                                                                                        :fi "Yksityinen tekstikenttä"
                                                                                        :sv "Privat textfält"}
                                                                          :field/optional false
                                                                          :field/type :text
                                                                          :field/max-length 100
                                                                          :field/privacy :private}]})

        form-private-nbn (create-form! {:actor owner
                                        :organization {:organization/id "nbn"}
                                        :form/internal-name "Simple form"
                                        :form/external-title {:en "Form"
                                                              :fi "Lomake"
                                                              :sv "Blankett"}
                                        :form/fields [{:field/title {:en "Simple text field"
                                                                     :fi "Yksinkertainen tekstikenttä"
                                                                     :sv "Textfält"}
                                                       :field/optional false
                                                       :field/type :text
                                                       :field/max-length 100
                                                       :field/privacy :private}]})

        form-private-thl (create-form! {:actor owner
                                        :organization {:organization/id "thl"}
                                        :form/internal-name "Simple form"
                                        :form/external-title {:en "Form"
                                                              :fi "Lomake"
                                                              :sv "Blankett"}
                                        :form/fields [{:field/title {:en "Simple text field"
                                                                     :fi "Yksinkertainen tekstikenttä"
                                                                     :sv "Textfält"}
                                                       :field/optional false
                                                       :field/type :text
                                                       :field/max-length 100
                                                       :field/privacy :private}]})
        form-private-hus (create-form! {:actor owner
                                        :organization {:organization/id "hus"}
                                        :form/internal-name "Simple form"
                                        :form/external-title {:en "Form"
                                                              :fi "Lomake"
                                                              :sv "Blankett"}
                                        :form/fields [{:field/title {:en "Simple text field"
                                                                     :fi "Yksinkertainen tekstikenttä"
                                                                     :sv "Textfält"}
                                                       :field/optional false
                                                       :field/type :text
                                                       :field/max-length 100
                                                       :field/privacy :private}]})
        form-organization-owner (create-all-field-types-example-form! organization-owner1 {:organization/id "organization1"} "Owned by organization owner" {:en "Owned by organization owner"
                                                                                                                                                            :fi "Omistaja organization owner"
                                                                                                                                                            :sv "Ägare organization owner"})

        ega-form (create-form! {:actor owner
                                :organization {:organization/id "csc"}
                                :form/internal-name "EGA Application Form"
                                :form/external-title {:en "EGA Form"
                                                      :fi "EGA Lomake"
                                                      :sv "EGA Blankett"}
                                :form/fields [{:field/title {:en "Description"
                                                             :fi "Kuvaus"
                                                             :sv "Text"}
                                               :field/optional false
                                               :field/type :text}]})

        ;; Create categories
        ordinary-category {:category/id (create-category! {:actor owner
                                                           :category/title {:en "Ordinary"
                                                                            :fi "Tavalliset"
                                                                            :sv "Vanliga"}})}
        technical-category {:category/id (create-category! {:actor owner
                                                            :category/title {:en "Technical"
                                                                             :fi "Tekniset"
                                                                             :sv "Teknisk"}})}

        special-category {:category/id (create-category! {:actor owner
                                                          :category/title {:en "Special"
                                                                           :fi "Erikoiset"
                                                                           :sv "Speciellt"}
                                                          :category/description {:en "Special catalogue items for demonstration purposes."
                                                                                 :fi "Erikoiset resurssit demoja varten."
                                                                                 :sv "Särskilda katalogposter för demonstration."}
                                                          :category/children [technical-category]})}]
    (create-archived-form! owner)

    ;; Create catalogue items
    (create-catalogue-item! {:actor owner
                             :title {:en "Master workflow"
                                     :fi "Master-työvuo"
                                     :sv "Master-arbetsflöde"}
                             :infourl {:en "http://www.google.com"
                                       :fi "http://www.google.fi"
                                       :sv "http://www.google.se"}
                             :resource-id res1
                             :form-id form
                             :organization {:organization/id "nbn"}
                             :workflow-id (:master workflows)
                             :categories [technical-category]})
    (create-catalogue-item! {:actor owner
                             :title {:en "Decider workflow"
                                     :fi "Päättäjätyövuo"
                                     :sv "Arbetsflöde för beslutsfattande"}
                             :infourl {:en "http://www.google.com"
                                       :fi "http://www.google.fi"
                                       :sv "http://www.google.se"}
                             :resource-id res1
                             :form-id form
                             :organization {:organization/id "nbn"}
                             :workflow-id (:decider workflows)
                             :categories [special-category]})
    (let [catid (create-catalogue-item! {:actor owner
                                         :title {:en "Default workflow"
                                                 :fi "Oletustyövuo"
                                                 :sv "Standard arbetsflöde"}
                                         :infourl {:en "http://www.google.com"
                                                   :fi "http://www.google.fi"
                                                   :sv "http://www.google.se"}
                                         :resource-id res1
                                         :form-id form
                                         :organization {:organization/id "nbn"}
                                         :workflow-id (:default workflows)
                                         :categories [ordinary-category]})]
      (create-applications! catid users))
    (create-catalogue-item! {:actor owner
                             :title {:en "Default workflow 2"
                                     :fi "Oletustyövuo 2"
                                     :sv "Standard arbetsflöde 2"}
                             :resource-id res2
                             :form-id form-private-thl
                             :organization {:organization/id "csc"}
                             :workflow-id (:default workflows)
                             :categories [ordinary-category]})
    (create-catalogue-item! {:actor owner
                             :title {:en "Default workflow 3"
                                     :fi "Oletustyövuo 3"
                                     :sv "Standard arbetsflöde 3"}
                             :resource-id res3
                             :form-id form-private-hus
                             :organization {:organization/id "hus"}
                             :workflow-id (:default workflows)
                             :categories [ordinary-category]})
    (create-catalogue-item! {:actor owner
                             :title {:en "CINECA synthetic cohort EUROPE UK1 referencing fake samples"
                                     :fi "CINECA synthetic cohort EUROPE UK1 referencing fake samples"
                                     :sv "CINECA synthetic cohort EUROPE UK1 referencing fake samples"}
                             :resource-id ega-resource
                             :form-id ega-form
                             :organization {:organization/id "csc"}
                             :workflow-id (:ega workflows)
                             :categories [special-category]})
    (create-catalogue-item! {:actor owner
                             :title {:en "Default workflow with extra license"
                                     :fi "Oletustyövuo ylimääräisellä lisenssillä"
                                     :sv "Arbetsflöde med extra licens"}
                             :resource-id res-with-extra-license
                             :form-id form
                             :organization {:organization/id "nbn"}
                             :workflow-id (:default workflows)
                             :categories [ordinary-category]})
    (create-catalogue-item! {:title {:en "Auto-approve workflow"
                                     :fi "Työvuo automaattisella hyväksynnällä"
                                     :sv "Arbetsflöde med automatisk godkänning"}
                             :infourl {:en "http://www.google.com"
                                       :fi "http://www.google.fi"
                                       :sv "http://www.google.se"}
                             :resource-id res1
                             :form-id form
                             :organization {:organization/id "nbn"}
                             :workflow-id (:auto-approve workflows)
                             :categories [special-category]})
    (create-bona-fide-catalogue-item! (merge users +bot-users+))
    (let [default-disabled (create-catalogue-item! {:actor owner
                                                    :title {:en "Default workflow (disabled)"
                                                            :fi "Oletustyövuo (pois käytöstä)"
                                                            :sv "Standard arbetsflöde (avaktiverat)"}
                                                    :resource-id res1
                                                    :form-id form
                                                    :organization {:organization/id "nbn"}
                                                    :workflow-id (:default workflows)
                                                    :categories [ordinary-category]})]
      (create-disabled-applications! default-disabled
                                     (users :applicant2)
                                     (users :approver1))
      (db/set-catalogue-item-enabled! {:id default-disabled :enabled false}))
    (let [default-expired (create-catalogue-item! {:actor owner
                                                   :title {:en "Default workflow (expired)"
                                                           :fi "Oletustyövuo (vanhentunut)"
                                                           :sv "Standard arbetsflöde (utgånget)"}
                                                   :resource-id res1
                                                   :form-id form
                                                   :organization {:organization/id "nbn"}
                                                   :workflow-id (:default workflows)
                                                   :categories [ordinary-category]})]
      (db/set-catalogue-item-endt! {:id default-expired :end (time/now)}))
    (create-catalogue-item! {:actor organization-owner1
                             :title {:en "Owned by organization owner"
                                     :fi "Organisaatio-omistajan omistama"
                                     :sv "Ägas av organisationägare"}
                             :resource-id res-organization-owner
                             :form-id form-organization-owner
                             :organization {:organization/id "organization1"}
                             :workflow-id (:organization-owner workflows)
                             :categories [special-category]})

    (let [applicant (users :applicant1)
          handler (users :approver2)
          reviewer (users :reviewer)
          catid-1 (create-catalogue-item! {:actor owner
                                           :title {:en "Default workflow with public and private fields"
                                                   :fi "Testityövuo julkisilla ja yksityisillä lomakekentillä"
                                                   :sv "Standard arbetsflöde med publika och privata textfält"}
                                           :resource-id res1
                                           :form-id form-with-public-and-private-fields
                                           :organization {:organization/id "nbn"}
                                           :workflow-id (:default workflows)
                                           :categories [ordinary-category]})
          catid-2 (create-catalogue-item! {:actor owner
                                           :title {:en "Default workflow with private form"
                                                   :fi "Oletustyövuo yksityisellä lomakkeella"
                                                   :sv "Standard arbetsflöde med privat blankett"}
                                           :resource-id res2
                                           :form-id form-private-nbn
                                           :organization {:organization/id "nbn"}
                                           :workflow-id (:default workflows)
                                           :categories [ordinary-category]})
          app-id (create-draft! applicant [catid-1 catid-2] "two-form draft application")]
      (command! {:type :application.command/submit
                 :application-id app-id
                 :actor applicant})
      (command! {:type :application.command/request-review
                 :application-id app-id
                 :actor handler
                 :reviewers [reviewer]
                 :comment "please have a look"}))

    (when (:enable-duo rems.config/env)
      (let [applicant (users :applicant1)
            handler (users :approver2)
            reviewer (users :reviewer)
            cat-id (create-catalogue-item! {:actor owner
                                            :title {:en "Default workflow with DUO codes"
                                                    :fi "Testityövuo DUO koodeilla"
                                                    :sv "Standard arbetsflöde med DUO koder"}
                                            :infourl {:en "http://www.google.com"
                                                      :fi "http://www.google.fi"
                                                      :sv "http://www.google.se"}
                                            :resource-id duo-resource
                                            :form-id form
                                            :organization {:organization/id "nbn"}
                                            :workflow-id (:default workflows)
                                            :categories [special-category]})
            app-id (create-draft! applicant [cat-id] "application with DUO codes")]
        (create-draft! applicant [cat-id] "draft application with DUO codes")
        (command! {:type :application.command/submit
                   :application-id app-id
                   :actor applicant})
        (command! {:type :application.command/request-review
                   :application-id app-id
                   :actor handler
                   :reviewers [reviewer]
                   :comment "please have a look"})))))

(defn create-organizations! [users]
  (let [owner (users :owner)
        organization-owner1 (users :organization-owner1)
        organization-owner2 (users :organization-owner2)]
    ;; Create organizations
    (create-organization! {:actor owner :users users})
    (create-organization! {:actor owner
                           :organization/id "hus"
                           :organization/name {:fi "Helsingin yliopistollinen sairaala" :en "Helsinki University Hospital" :sv "Helsingfors Universitetssjukhus"}
                           :organization/short-name {:fi "HUS" :en "HUS" :sv "HUS"}
                           :organization/owners [{:userid organization-owner1}]
                           :organization/review-emails []})
    (create-organization! {:actor owner
                           :organization/id "thl"
                           :organization/name {:fi "Terveyden ja hyvinvoinnin laitos" :en "Finnish institute for health and welfare" :sv "Institutet för hälsa och välfärd"}
                           :organization/short-name {:fi "THL" :en "THL" :sv "THL"}
                           :organization/owners [{:userid organization-owner2}]
                           :organization/review-emails []})
    (create-organization! {:actor owner
                           :organization/id "nbn"
                           :organization/name {:fi "NBN" :en "NBN" :sv "NBN"}
                           :organization/short-name {:fi "NBN" :en "NBN" :sv "NBN"}
                           :organization/owners [{:userid organization-owner2}]
                           :organization/review-emails []})
    (create-organization! {:actor owner
                           :organization/id "abc"
                           :organization/name {:fi "ABC" :en "ABC" :sv "ABC"}
                           :organization/short-name {:fi "ABC" :en "ABC" :sv "ABC"}
                           :organization/owners []
                           :organization/review-emails [{:name {:fi "ABC Kirjaamo"} :email "kirjaamo@abc.efg"}]})
    (create-organization! {:actor owner
                           :organization/id "csc"
                           :organization/name {:fi "CSC – TIETEEN TIETOTEKNIIKAN KESKUS OY" :en "CSC – IT CENTER FOR SCIENCE LTD." :sv "CSC – IT CENTER FOR SCIENCE LTD."}
                           :organization/short-name {:fi "CSC" :en "CSC" :sv "CSC"}
                           :organization/owners []
                           :organization/review-emails []})
    (create-organization! {:actor owner
                           :organization/id "organization1"
                           :organization/name {:fi "Organization 1" :en "Organization 1" :sv "Organization 1"}
                           :organization/short-name {:fi "ORG 1" :en "ORG 1" :sv "ORG 1"}
                           :organization/owners [{:userid organization-owner1}]
                           :organization/review-emails []})
    (create-organization! {:actor owner
                           :organization/id "organization2"
                           :organization/name {:fi "Organization 2" :en "Organization 2" :sv "Organization 2"}
                           :organization/short-name {:fi "ORG 2" :en "ORG 2" :sv "ORG 2"}
                           :organization/owners [{:userid organization-owner2}]
                           :organization/review-emails []})))

(defn create-test-api-key! []
  (api-key/add-api-key! +test-api-key+ {:comment "test data"}))

(defn create-owners!
  "Create an owner, two organization owners, and their organizations."
  []
  (create-test-api-key!)
  (create-user! (+fake-user-data+ "owner") :owner)
  (create-user! (+fake-user-data+ "organization-owner1"))
  (create-user! (+fake-user-data+ "organization-owner2"))
  (create-organization! {:actor "owner" :users +fake-users+})
  (create-organization! {:actor "owner"
                         :organization/id "organization1"
                         :organization/name {:fi "Organization 1" :en "Organization 1" :sv "Organization 1"}
                         :organization/short-name {:fi "ORG 1" :en "ORG 1" :sv "ORG 1"}
                         :organization/owners [{:userid "organization-owner1"}]
                         :organization/review-emails []})
  (create-organization! {:actor "owner"
                         :organization/id "organization2"
                         :organization/name {:fi "Organization 2" :en "Organization 2" :sv "Organization 2"}
                         :organization/short-name {:fi "ORG 2" :en "ORG 2" :sv "ORG 2"}
                         :organization/owners [{:userid "organization-owner2"}]
                         :organization/review-emails []}))

(defn create-test-data! []
  (assert-no-existing-data!)
  (create-test-api-key!)
  (create-test-users-and-roles!)
  (create-organizations! +fake-users+)
  (create-bots!)
  (create-items! +fake-users+))

(defn create-demo-data! []
  (assert-no-existing-data!)
  (let [[users user-data] (case (:authentication rems.config/env)
                            :oidc [+oidc-users+ +oidc-user-data+]
                            [+demo-users+ +demo-user-data+])]
    (api-key/add-api-key! 55 {:comment "Finna"})
    (create-users-and-roles! users user-data)
    (create-organizations! users)
    (create-bots!)
    (create-items! users)))

(comment
  (do ; you can manually re-create test data (useful sometimes when debugging)
    (luminus-migrations.core/migrate ["reset"] (select-keys rems.config/env [:database-url]))
    (create-test-data!)
    (create-performance-test-data!)))

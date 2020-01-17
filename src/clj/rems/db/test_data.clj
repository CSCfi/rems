(ns rems.db.test-data
  "Populating the database with nice test data."
  (:require [clj-time.core :as time]
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [medley.core :refer [map-vals]]
            [rems.api.schema :as schema]
            [rems.api.services.catalogue :as catalogue]
            [rems.api.services.command :as command]
            [rems.api.services.licenses :as licenses]
            [rems.api.services.resource :as resource]
            [rems.api.services.workflow :as workflow]
            [rems.application.approver-bot :as approver-bot]
            [rems.db.api-key :as api-key]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.form :as form]
            [rems.db.roles :as roles]
            [rems.db.users :as users]
            [ring.util.http-response :refer [bad-request!]])
  (:import [java.util UUID]
           [java.util.concurrent Executors Future]))

;;; test data definitions

(def +fake-users+
  {:applicant1 "alice"
   :applicant2 "malice"
   :approver1 "developer"
   :approver2 "handler"
   :owner "owner"
   :reporter "reporter"
   :reviewer "carl"
   :roleless1 "elsa"
   :roleless2 "frank"
   :approver-bot approver-bot/bot-userid})

(def +fake-user-data+
  {"developer" {:eppn "developer" :mail "developer@example.com" :commonName "Developer"}
   "alice" {:eppn "alice" :mail "alice@example.com" :commonName "Alice Applicant"}
   "malice" {:eppn "malice" :mail "malice@example.com" :commonName "Malice Applicant" :twinOf "alice" :other "Attribute Value"}
   "handler" {:eppn "handler" :mail "handler@example.com" :commonName "Hannah Handler"}
   "carl" {:eppn "carl" :mail "carl@example.com" :commonName "Carl Reviewer"}
   "elsa" {:eppn "elsa" :mail "elsa@example.com" :commonName "Elsa Roleless"}
   "frank" {:eppn "frank" :mail "frank@example.com" :commonName "Frank Roleless" :organization "frank"}
   "owner" {:eppn "owner" :mail "owner@example.com" :commonName "Owner"}
   "reporter" {:eppn "reporter" :mail "reporter@example.com" :commonName "Reporter"}
   approver-bot/bot-userid {:eppn approver-bot/bot-userid :commonName "Approver Bot"}})

(def +demo-users+
  {:applicant1 "RDapplicant1@funet.fi"
   :applicant2 "RDapplicant2@funet.fi"
   :approver1 "RDapprover1@funet.fi"
   :approver2 "RDapprover2@funet.fi"
   :reviewer "RDreview@funet.fi"
   :owner "RDowner@funet.fi"
   :reporter "RDdomainreporter@funet.fi"
   :approver-bot approver-bot/bot-userid})

(def +demo-user-data+
  {"RDapplicant1@funet.fi" {:eppn "RDapplicant1@funet.fi" :mail "RDapplicant1.test@test_example.org" :commonName "RDapplicant1 REMSDEMO1"}
   "RDapplicant2@funet.fi" {:eppn "RDapplicant2@funet.fi" :mail "RDapplicant2.test@test_example.org" :commonName "RDapplicant2 REMSDEMO"}
   "RDapprover1@funet.fi" {:eppn "RDapprover1@funet.fi" :mail "RDapprover1.test@rems_example.org" :commonName "RDapprover1 REMSDEMO"}
   "RDapprover2@funet.fi" {:eppn "RDapprover2@funet.fi" :mail "RDapprover2.test@rems_example.org" :commonName "RDapprover2 REMSDEMO"}
   "RDreview@funet.fi" {:eppn "RDreview@funet.fi" :mail "RDreview.test@rems_example.org" :commonName "RDreview REMSDEMO"}
   "RDowner@funet.fi" {:eppn "RDowner@funet.fi" :mail "RDowner.test@test_example.org" :commonName "RDowner REMSDEMO"}
   "RDdomainreporter@funet.fi" {:eppn "RDdomainreporter@funet.fi" :mail "RDdomainreporter.test@test_example.org" :commonName "RDdomainreporter REMSDEMO"}
   approver-bot/bot-userid {:eppn approver-bot/bot-userid :commonName "Approver Bot"}})

(def +oidc-users+
  {:applicant1 "WHFS36UEZD6TNURJ76WYLSVDCUUENOOF"
   :applicant2 "C567LI5QAACWKC7YYA74BJ2X7DH7EEYI"
   :approver1 "EKGFNAAGCHIQ5ERUUFS2RCZ44IHYZPEA"
   :approver2 "7R3JYB32PL3EPVD34RWIAWDZSEOXW4OQ"
   :reviewer "F3OJL757ACT4QXVXZZ4F7VG6HQGBEC4M"
   :reporter "JOBDHBMX4EFXQC5IPQVXPP4FFWJ6XQYL"
   :owner "BACZQAPVWBDJ2OXLKT2WWW5LT5LV6YR4"})

(def +oidc-user-data+
  {"WHFS36UEZD6TNURJ76WYLSVDCUUENOOF" {:eppn "WHFS36UEZD6TNURJ76WYLSVDCUUENOOF" :mail "RDapplicant1@mailinator.com" :commonName "RDapplicant1 REMSDEMO1"}
   "C567LI5QAACWKC7YYA74BJ2X7DH7EEYI" {:eppn "C567LI5QAACWKC7YYA74BJ2X7DH7EEYI" :mail "RDapplicant2@mailinator.com" :commonName "RDapplicant2 REMSDEMO"}
   "EKGFNAAGCHIQ5ERUUFS2RCZ44IHYZPEA" {:eppn "EKGFNAAGCHIQ5ERUUFS2RCZ44IHYZPEA" :mail "RDapprover1@mailinator.com" :commonName "RDapprover1 REMSDEMO"}
   "7R3JYB32PL3EPVD34RWIAWDZSEOXW4OQ" {:eppn "7R3JYB32PL3EPVD34RWIAWDZSEOXW4OQ" :mail "RDapprover2@mailinator.com" :commonName "RDapprover2 REMSDEMO"}
   "F3OJL757ACT4QXVXZZ4F7VG6HQGBEC4M" {:eppn "F3OJL757ACT4QXVXZZ4F7VG6HQGBEC4M" :mail "RDreview@mailinator.com" :commonName "RDreview REMSDEMO"}
   "JOBDHBMX4EFXQC5IPQVXPP4FFWJ6XQYL" {:eppn "JOBDHBMX4EFXQC5IPQVXPP4FFWJ6XQYL" :mail "RDdomainreporter@mailinator.com" :commonName "RDdomainreporter REMSDEMO"}
   "BACZQAPVWBDJ2OXLKT2WWW5LT5LV6YR4" {:eppn "BACZQAPVWBDJ2OXLKT2WWW5LT5LV6YR4" :mail "RDowner@mailinator.com" :commonName "RDowner REMSDEMO"}})

;;; helpers for generating test data

(defn command! [command]
  (let [command (merge {:time (time/now)}
                       command)
        result (command/command! command)]
    (assert (not (:errors result))
            {:command command :result result})
    result))

(defn- transpose-localizations [m]
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
    (users/add-user! user user-attributes)
    (doseq [role roles]
      (roles/add-role! user role))))

(defn create-license! [{:keys [actor]
                        :license/keys [type title link organization text attachment-id]
                        :as command}]
  (let [result (licenses/create-license! {:licensetype (name (or type :text))
                                          :organization (or organization "")
                                          :localizations
                                          (transpose-localizations {:title title
                                                                    :textcontent (merge link text)
                                                                    :attachment-id attachment-id})}
                                         (or actor "owner"))]
    (assert (:success result) {:command command :result result})
    (:id result)))

(defn create-attachment-license! [{:keys [actor]
                                   :license/keys [organization]}]
  (let [fi-attachment (:id (db/create-license-attachment! {:user (or actor "owner")
                                                           :filename "fi.txt"
                                                           :type "text/plain"
                                                           :data (.getBytes "Suomenkielinen lisenssi.")}))
        en-attachment (:id (db/create-license-attachment! {:user (or actor "owner")
                                                           :filename "en.txt"
                                                           :type "text/plain"
                                                           :data (.getBytes "License in English.")}))]
    (create-license! {:actor actor
                      :license/type :attachment
                      :license/organization organization
                      :license/title {:fi "Liitelisenssi" :en "Attachment license"}
                      :license/text {:fi "fi" :en "en"}
                      :license/attachment-id {:fi fi-attachment :en en-attachment}})))

(defn create-form! [{:keys [actor]
                     :form/keys [organization title fields]
                     :as command}]
  (let [result (form/create-form! (or actor "owner")
                                  {:form/organization (or organization "abc")
                                   :form/title (or title "")
                                   :form/fields (or fields [])})]
    (assert (:success result) {:command command :result result})
    (:id result)))

(defn create-resource! [{:keys [actor organization resource-ext-id license-ids]
                         :as command}]
  (let [result (resource/create-resource! {:resid (or resource-ext-id (str "urn:uuid:" (UUID/randomUUID)))
                                           :organization (or organization "abc")
                                           :licenses (or license-ids [])}
                                          (or actor "owner"))]
    (assert (:success result) {:command command :result result})
    (:id result)))

(defn create-workflow! [{:keys [actor organization title type handlers]
                         :as command}]
  (let [result (workflow/create-workflow!
                {:user-id (or actor "owner")
                 :organization (or organization "abc")
                 :title (or title "")
                 :type (or type :workflow/master)
                 :handlers
                 (or handlers
                     (do (create-user! (get +fake-user-data+ "developer"))
                         ["developer"]))})]
    (assert (:success result) {:command command :result result})
    (:id result)))

(defn create-catalogue-item! [{:keys [title resource-id form-id workflow-id infourl organization]
                               :as command}]
  (let [localizations (into {}
                            (for [lang (set (concat (keys title) (keys infourl)))]
                              [lang {:title (get title lang)
                                     :infourl (get infourl lang)}]))
        result (catalogue/create-catalogue-item!
                {:resid (or resource-id (create-resource! {}))
                 :form (or form-id (create-form! {}))
                 :organization (or organization "")
                 :wfid (or workflow-id (create-workflow! {}))
                 :localizations (or localizations {})})]
    (assert (:success result) {:command command :result result})
    (:id result)))

(defn create-application! [{:keys [catalogue-item-ids actor]}]
  (:application-id (command! {:type :application.command/create
                              :catalogue-item-ids (or catalogue-item-ids [(create-catalogue-item! {})])
                              :actor actor})))

(defn- base-command [{:keys [application-id actor time]}]
  (assert application-id)
  (assert actor)
  {:application-id application-id
   :actor actor
   :time (or time (time/now))})

(defn fill-form! [{:keys [application-id actor field-value] :as command}]
  (let [app (applications/get-application actor application-id)]
    (command! (assoc (base-command command)
                     :type :application.command/save-draft
                     :field-values (->> (:form/fields (:application/form app))
                                        (filter #(not (:field/optional %)))
                                        (map (fn [field]
                                               {:field (:field/id field)
                                                :value (or field-value "x")})))))))

(defn accept-licenses! [{:keys [application-id actor] :as command}]
  (let [app (applications/get-application actor application-id)]
    (command! (assoc (base-command command)
                     :type :application.command/accept-licenses
                     :accepted-licenses (map :license/id (:application/licenses app))))))

(defn create-draft! [actor catalogue-item-ids description]
  (let [app-id (create-application! {:catalogue-item-ids catalogue-item-ids
                                     :actor actor})]
    (fill-form! {:application-id app-id
                 :actor actor
                 :field-value description})
    (accept-licenses! {:application-id app-id
                       :actor actor})
    app-id))

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

(defn- create-archived-form! []
  (let [id (create-form! {:actor (+fake-users+ :owner)
                          :form/organization "nbn"
                          :form/title "Archived form, should not be seen by applicants"})]
    (form/set-form-archived! {:id id :archived true})))

(defn- create-disabled-license! [{:keys [actor]
                                  :license/keys [organization]}]
  (let [id (create-license! {:actor actor
                             :license/type "link"
                             :license/organization organization
                             :license/title {:en "Disabled license"
                                             :fi "Käytöstä poistettu lisenssi"}
                             :license/link {:en "http://disabled"
                                            :fi "http://disabled"}})]
    (db/set-license-enabled! {:id id :enabled false})))

(def ^:private all-field-types-example
  [{:field/title {:en "This form demonstrates all possible field types. (This text itself is a label field.)"
                  :fi "Tämä lomake havainnollistaa kaikkia mahdollisia kenttätyyppejä. (Tämä teksti itsessään on lisätietokenttä.)"}
    :field/optional false
    :field/type :label}

   {:field/title {:en "Application title field"
                  :fi "Hakemuksen otsikko -kenttä"}
    :field/optional false
    :field/type :description}

   {:field/title {:en "Text field"
                  :fi "Tekstikenttä"}
    :field/optional false
    :field/type :text
    :field/placeholder {:en "Placeholder text"
                        :fi "Täyteteksti"}}

   {:field/title {:en "Text area"
                  :fi "Tekstialue"}
    :field/optional false
    :field/type :texta
    :field/placeholder {:en "Placeholder text"
                        :fi "Täyteteksti"}}

   {:field/title {:en "Header"
                  :fi "Otsikko"}
    :field/type :header
    :field/optional false}

   {:field/title {:en "Date field"
                  :fi "Päivämääräkenttä"}
    :field/optional true
    :field/type :date}

   {:field/title {:en "Email field"
                  :fi "Sähköpostikenttä"}
    :field/optional true
    :field/type :email}

   {:field/title {:en "Attachment"
                  :fi "Liitetiedosto"}
    :field/optional true
    :field/type :attachment}

   {:field/title {:en "Option list"
                  :fi "Valintalista"}
    :field/optional true
    :field/type :option
    :field/options [{:key "Option1"
                     :label {:en "First option"
                             :fi "Ensimmäinen vaihtoehto"}}
                    {:key "Option2"
                     :label {:en "Second option"
                             :fi "Toinen vaihtoehto"}}
                    {:key "Option3"
                     :label {:en "Third option"
                             :fi "Kolmas vaihtoehto"}}]}

   {:field/title {:en "Multi-select list"
                  :fi "Monivalintalista"}
    :field/optional true
    :field/type :multiselect
    :field/options [{:key "Option1"
                     :label {:en "First option"
                             :fi "Ensimmäinen vaihtoehto"}}
                    {:key "Option2"
                     :label {:en "Second option"
                             :fi "Toinen vaihtoehto"}}
                    {:key "Option3"
                     :label {:en "Third option"
                             :fi "Kolmas vaihtoehto"}}]}

   {:field/title {:en "The following field types can have a max length."
                  :fi "Seuraavilla kenttätyypeillä voi olla pituusrajoitus."}
    :field/optional false
    :field/type :label}

   ;; fields which support maxlength
   {:field/title {:en "Text field with max length"
                  :fi "Tekstikenttä pituusrajalla"}
    :field/optional true
    :field/type :text
    :field/max-length 10}

   {:field/title {:en "Text area with max length"
                  :fi "Tekstialue pituusrajalla"}
    :field/optional true
    :field/type :texta
    :field/max-length 100}])

(deftest test-all-field-types-example
  (is (= (:vs (:field/type schema/FieldTemplate))
         (set (map :field/type all-field-types-example)))
      "a new field has been added to schema but not to this test data"))

(defn- create-all-field-types-example-form!
  "Creates a bilingual form with all supported field types. Returns the form ID."
  [users]
  (create-form!
   {:actor (users :owner)
    :form/organization "nbn"
    :form/title "Example form with all field types"
    :form/fields all-field-types-example}))

(defn create-thl-demo-form!
  [users]
  (create-form!
   {:actor (users :owner)
    :form/organization "nbn"
    :form/title "THL form"
    :form/fields [{:field/title {:en "Application title"
                                 :fi "Hakemuksen otsikko"}
                   :field/optional true
                   :field/type :description
                   :field/placeholder {:en "Study of.."
                                       :fi "Tutkimus aiheesta.."}}
                  {:field/title {:en "1. Research project full title"
                                 :fi "1. Tutkimusprojektin täysi nimi"}
                   :field/optional false
                   :field/type :texta
                   :field/max-length 100}
                  {:field/title {:en "2. This is an amendment of a previous approved application"
                                 :fi "2. Hakemus täydentää edellistä hakemusta"}
                   :field/optional false
                   :field/type :option
                   :field/options [{:key "false"
                                    :label {:en "no"
                                            :fi "ei"}}
                                   {:key "true"
                                    :label {:en "yes"
                                            :fi "kyllä"}}]}
                  {:field/title {:en "If yes, what were the previous project permit code/s?"
                                 :fi "Jos kyllä, mitkä olivat edelliset projektin lupakoodit?"}
                   :field/optional true
                   :field/type :text}
                  {:field/title {:en "3. Study PIs (name, titile, affiliation, email)"
                                 :fi "3. Henkilöstö (nimi, titteli, yhteys projektiin, sähköposti)"}
                   :field/optional false
                   :field/type :texta
                   :field/max-length 100}
                  {:field/title {:en "4. Contact person for application if different than applicant (name, email)"
                                 :fi "4. Yhteyshenkilö, jos ei sama kuin hakija (nimi, sähköposti)"}
                   :field/optional true
                   :field/type :texta
                   :field/max-length 100}
                  {:field/title {:en "5. Research project start date"
                                 :fi "5. Projektin aloituspäivä"}
                   :field/optional false
                   :field/type :date}
                  {:field/title {:en "6. Research project end date"
                                 :fi "6. Projektin lopetuspäivä"}
                   :field/optional false
                   :field/type :date}
                  {:field/title {:en "7. Describe in detail the aims of the study and analysis plan"
                                 :fi "7. Kuvaile yksityiskohtaisesti tutkimussuunnitelma"}
                   :field/optional false
                   :field/type :texta
                   :field/max-length 100}
                  {:field/title {:en "8. If this is an amendment, please describe briefly what is new"
                                 :fi "8. Jos tämä on täydennys edelliseen hakemukseen, kuvaile tiiviisti, mikä on muuttunut."}
                   :field/optional true
                   :field/type :texta
                   :field/max-length 100}
                  {:field/title {:en "9. Public description of the project (in Finnish, when possible), to be published in THL Biobank."
                                 :fi "9. Kuvaile yksityiskohtaisesti tutkimussuunnitelma"}
                   :field/placeholder {:en "Meant for sample donors and for anyone interested in the research done using THL Biobank's sample collections. This summary and the name of the Study PI will be published in THL Biobank's web pages."
                                       :fi "Tarkoitettu aineistojen lahjoittajille ja kaikille, joita kiinnostaa THL:n Biopankkia käyttävät tutkimusprojektit. Tämä kuvaus sekä tutkijan nimi julkaistaan THL:n nettisivuilla, kun sopimus on allekirjoitettu."}
                   :field/optional false
                   :field/type :texta
                   :field/max-length 100}
                  {:field/title {:en "10. Place/plces of research, including place of sample and/or data analysis."
                                 :fi "10. Tutkimuksen yysinen sijainti, mukaanlukien paikka, missä data-analyysi toteutetaan."}
                   :field/placeholder {:en "List all research center involved in this study, and each center's role. Specify which centers will analyze which data and/or samples.."
                                       :fi "Listaa kaikki tutkimuskeskukset, jotka osallistuvat tähän tutkimukseen, ml. niiden roolit tutkimuksessa. Erittele, missä analysoidaan mikäkin näyte."}
                   :field/optional false
                   :field/type :texta
                   :field/max-length 100}
                  {:field/title {:en "11. Description of other research group members and their role in the applied project."
                                 :fi "11. Kuvaus muista tutkimukseen osallistuvista henkilöistä, ja heidän roolistaan projektissa."}
                   :field/placeholder {:en "For every group member: name, title, affiliation, contact information. In addition describe earch member's role in the project (e.g. cohor representative, data analyst, etc.)"
                                       :fi "Anna jokaisesta jäsenestä: nimi, titteli, yhteys projektiin, yhteystiedot. Kuvaile lisäki jokaisen henkilön rooli projektissa."}
                   :field/optional false
                   :field/type :texta
                   :field/max-length 100}
                  {:field/title {:en "12. Specify selection criteria of study participants (if applicable)"
                                 :fi "12. Erottele tukimuksen osallistujien valintakriteerit (jos käytetty)"}
                   :field/placeholder {:en "Describe any specific criteria by which study participans will be selected. For example, selection for specific age group, gender, area/locality, disease status etc."
                                       :fi "Kuvaa tarkat valintakriteerit, joilla tutkimuksen osallistujat valitaan. Esimerkiksi ikäryhmä, sukupuoli, alue, taudin tila jne."}
                   :field/optional false
                   :field/type :texta
                   :field/max-length 100}
                  {:field/title {:en "13. Specify requested phenotype data (information on variables is found at https://kite.fimm.fi)"
                                 :fi "13. Tarkenna pyydetty fenotyyppidatta (tietoa muuttujista on saatavilla osoitteesta https://kite.fimm.fi)"}
                   :field/placeholder {:en "Desrcibe in detail the phenotype data needed for the study. Lists of variables are to be attached to the application (below)."
                                       :fi "Kuvaile yksityiskohtaisesti tutkimukseen tarvittava fenotyyppidata. Lista muuttujista lisätään hakemukseen liitteenä."}
                   :field/optional false
                   :field/type :texta
                   :field/max-length 100}
                  {:field/title {:en "14. Specify requested genomics or other omics data (if applicable)"
                                 :fi "14. Kuvaile tarvittava genomiikkadata."}
                   :field/placeholder {:en "Specify in detail the requested data format for different genomics or other omics data types. Information of available omics data is found at THL Biobank web page (www.thl.fi/biobank/researchers)"
                                       :fi "Kuvaile tarvitsemasi genomiikkadata. Lisätietoa saatavilla osoitteesta www.thl.fi/biobank/researchers"}
                   :field/optional true
                   :field/type :texta
                   :field/max-length 100}
                  {:field/title {:en "16. Are biological samples requested?"
                                 :fi "16. Pyydetäänkö biologisia näytteitä?"}
                   :field/optional false
                   :field/type :option
                   :field/options [{:key "false"
                                    :label {:en "no"
                                            :fi "ei"}}
                                   {:key "true"
                                    :label {:en "yes"
                                            :fi "kyllä"}}]}
                  {:field/title {:en "The type and amount of biological samples requested"
                                 :fi "Biologisten näytteiden tyypit ja määrät."}
                   :field/placeholder {:en "Type and amount of samples and any additional specific criteria."
                                       :fi "Biologisten näytteiden määrät, tyypit, ja mahdolliset muut kriteerit."}
                   :field/optional true
                   :field/type :texta
                   :field/max-length 100}
                  {:field/title {:en "17. What study results will be returned to THL Biobank (if any)?"
                                 :fi "17. Mitä tutkimustuloksia tullaan palauttamaan THL Biopankkiin?"}
                   :field/placeholder {:en "Study results such as new laboratory measurements, produced omics data and other analysis data (\"raw data\")"
                                       :fi "Tutkimustuloksia kuten mittaustuloksia, uutta biologista dataa, tai muita analyysien tuloksia (\"raaka-dataa\")"}
                   :field/optional false
                   :field/type :texta
                   :field/max-length 100}
                  {:field/title {:en "Expected date for return of study results"
                                 :fi "Odotettu tutkimustuloksien palautuspäivämäärä"}
                   :field/optional true
                   :field/type :date}
                  {:field/title {:en "18. Ethical aspects of the project"
                                 :fi "18. Tutkimuksen eettiset puolet"}
                   :field/placeholder {:en "If you have any documents from an ethical board, please provide them as an attachment."
                                       :fi "Liitä mahdolliset eettisen toimikunnan lausunnot hakemuksen loppuun."}
                   :field/optional false
                   :field/type :texta
                   :field/max-length 100}
                  {:field/title {:en "19. Project keywords (max 5)"
                                 :fi "19. Projektin avainsanat (maks. 5)"}
                   :field/placeholder {:en "List a few keywords that are related to this research project (please separate with comma)"
                                       :fi "Listaa muutama projektiin liittyvä avainsana, pilkuilla erotettuina."}
                   :field/optional false
                   :field/type :texta
                   :field/max-length 100}
                  {:field/title {:en "20. Planned publications (max 3)"
                                 :fi "20. Suunnitellut julkaisut (maks. 3)"}
                   :field/placeholder {:en "Planned publication titles / research topics"
                                       :fi "Suunniteltujen julkaisujen otsikot / tutkimusaiheet"}
                   :field/optional false
                   :field/type :texta
                   :field/max-length 100}
                  {:field/title {:en "21. Funding information"
                                 :fi "21. Rahoitus"}
                   :field/placeholder {:en "List all funding sources which will be used for this research project."
                                       :fi "Listaa kaikki rahoituslähteet joita tullaan käyttämään tähän tutkimusprojektiin"}
                   :field/optional false
                   :field/type :texta
                   :field/max-length 100}
                  {:field/title {:en "22. Invoice address (Service prices: www.thl.fi/biobank/researchers)"
                                 :fi "22. Laskutusosoite (Palveluhinnasto: www.thl.fi/biobank/researchers)"}
                   :field/placeholder {:en "Electronic invoice address when possible + invoicing reference"
                                       :fi "Sähköinen laskutus, kun mahdollista. Lisäksi viitenumero."}
                   :field/optional false
                   :field/type :texta
                   :field/max-length 100}
                  {:field/title {:en "23. Other information"
                                 :fi "23. Muuta"}
                   :field/placeholder {:en "Any other relevant information for the application"
                                       :fi "Muuta hakemukseen liittyvää oleellista tietoa"}
                   :field/optional true
                   :field/type :texta
                   :field/max-length 100}
                  {:field/title {:en "THL Biobank's registered area/s of operation to which the research project complies:"
                                 :fi "THL Biobankin toimialueet, joihin tutkimusprojekti liittyy:"}
                   :field/optional false
                   :field/type :multiselect
                   :field/options [{:key "population_health"
                                    :label {:en "Promoting the population's health"
                                            :fi "Edistää kansanterveytttä"}}
                                   {:key "disease_mechanisms"
                                    :label {:en "Identifying factors involved in disease mechanisms"
                                            :fi "Tunnistaa tautien mekanismeja"}}
                                   {:key "disease_prevention"
                                    :label {:en "Disease prevention"
                                            :fi "Estää tautien leviämistä"}}
                                   {:key "health_product_development"
                                    :label {:en "Developing products that promote the welfare and health of the population"
                                            :fi "Kehittää tuotteita, jotka edistävät kansanterveyttä."}}
                                   {:key "treatment_development"
                                    :label {:en "Developing products and treatments for diseases"
                                            :fi "Kehittää tuotteita ja parannuskeinoja tautien varalle"}}
                                   {:key "other"
                                    :label {:en "Other"
                                            :fi "Muuta"}}]}
                  {:field/title {:en "Other, specify"
                                 :fi "Muuta, tarkenna"}
                   :field/optional true
                   :field/type :texta
                   :field/max-length 100}
                  {:field/title {:en "Data management plan (pdf)"
                                 :fi "Datanhallintasuunnitelma (pdf)"}
                   :field/optional true
                   :field/type :attachment}]}))

(defn- create-workflows! [users]
  (let [approver1 (users :approver1)
        approver2 (users :approver2)
        owner (users :owner)
        default (create-workflow! {:actor owner
                                   :organization "nbn"
                                   :title "Default workflow"
                                   :type :workflow/default
                                   :handlers [approver1 approver2]})
        decider (create-workflow! {:actor owner
                                   :organization "nbn"
                                   :title "Decider workflow"
                                   :type :workflow/decider
                                   :handlers [approver1 approver2]})
        master (create-workflow! {:actor owner
                                  :organization "nbn"
                                  :title "Master workflow"
                                  :type :workflow/master
                                  :handlers [approver1 approver2]})]

    ;; attach both kinds of licenses to all workflows
    (let [link (create-license! {:actor owner
                                 :license/type :link
                                 :license/organization "nbn"
                                 :license/title {:en "CC Attribution 4.0"
                                                 :fi "CC Nimeä 4.0"}
                                 :license/link {:en "https://creativecommons.org/licenses/by/4.0/legalcode"
                                                :fi "https://creativecommons.org/licenses/by/4.0/legalcode.fi"}})
          text (create-license! {:actor owner
                                 :license/type :text
                                 :license/organization "nbn"
                                 :license/title {:en "General Terms of Use"
                                                 :fi "Yleiset käyttöehdot"}
                                 :license/text {:en (apply str (repeat 10 "License text in English. "))
                                                :fi (apply str (repeat 10 "Suomenkielinen lisenssiteksti. "))}})]
      (doseq [licid [link text]]
        (doseq [wfid [default decider master]]
          (db/create-workflow-license! {:wfid wfid :licid licid}))))

    {:default default
     :decider decider
     :master master}))

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

(defn create-performance-test-data! []
  (log/info "Creating performance test data")
  (let [resource-count 1000
        application-count 1000
        user-count 1000
        handlers [(+fake-users+ :approver1)
                  (+fake-users+ :approver2)]
        owner (+fake-users+ :owner)
        workflow-id (create-workflow! {:actor owner
                                       :organization "perf"
                                       :title "Performance tests"
                                       :handlers handlers})
        form-id (create-form!
                 {:actor owner
                  :form/organization "perf"
                  :form/title "Performance tests"
                  :form/fields [{:field/title {:en "Project name"
                                               :fi "Projektin nimi"}
                                 :field/optional false
                                 :field/type :description
                                 :field/placeholder {:en "Project"
                                                     :fi "Projekti"}}

                                {:field/title {:en "Project description"
                                               :fi "Projektin kuvaus"}
                                 :field/optional false
                                 :field/type :texta
                                 :field/placeholder {:en "The purpose of the project is to..."
                                                     :fi "Projektin tarkoitus on..."}}]})
        form (form/get-form-template form-id)
        license-id (create-license! {:actor owner
                                     :license/type :text
                                     :license/organization "perf"
                                     :license/title {:en "Performance License"
                                                     :fi "Suorituskykylisenssi"}
                                     :license/text {:en "Be fast."
                                                    :fi "Ole nopea."}})
        cat-item-ids (vec (in-parallel
                           (for [n (range-1 resource-count)]
                             (fn []
                               (let [resource-id (create-resource! {:organization "perf"
                                                                    :license-ids [license-id]})]
                                 (create-catalogue-item! {:title {:en (str "Performance test resource " n)
                                                                  :fi (str "Suorituskykytestiresurssi " n)}
                                                          :resource-id resource-id
                                                          :form-id form-id
                                                          :organization "perf"
                                                          :workflow-id workflow-id}))))))
        user-ids (vec (in-parallel
                       (for [n (range-1 user-count)]
                         (fn []
                           (let [user-id (str "perftester" n)]
                             (users/add-user! user-id {:eppn user-id
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
                                            :actor user-id})]
           (command! {:type :application.command/save-draft
                      :application-id app-id
                      :actor user-id
                      :field-values [{:field (:field/id (first (:form/fields form)))
                                      :value (str "Performance test application " (UUID/randomUUID))}
                                     {:field (:field/id (second (:form/fields form)))
                                      ;; 5000 characters (10 KB) of lorem ipsum generated with www.lipsum.com
                                      ;; to increase the memory requirements of an application
                                      :value (str "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Ut non diam vel erat dapibus facilisis vel vitae nunc. Curabitur at fermentum lorem. Cras et bibendum ante. Etiam convallis erat justo. Phasellus cursus molestie vehicula. Etiam molestie tellus vitae consectetur dignissim. Pellentesque euismod hendrerit mi sed tincidunt. Integer quis lorem ut ipsum egestas hendrerit. Aenean est nunc, mattis euismod erat in, sodales rutrum mauris. Praesent sit amet risus quis felis congue ultricies. Nulla facilisi. Sed mollis justo id tristique volutpat.\n\nPhasellus augue mi, facilisis ac velit et, pharetra tristique nunc. Pellentesque eget arcu quam. Curabitur dictum nulla varius hendrerit varius. Proin vulputate, ex lacinia commodo varius, ipsum velit viverra est, eget molestie dui nisi non eros. Nulla lobortis odio a magna mollis placerat. Interdum et malesuada fames ac ante ipsum primis in faucibus. Integer consectetur libero ut gravida ullamcorper. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas. Donec aliquam feugiat mollis. Quisque massa lacus, efficitur vel justo vel, elementum mollis magna. Maecenas at sem sem. Praesent sed ex mattis, egestas dui non, volutpat lorem. Nulla tempor, nisi rutrum accumsan varius, tellus elit faucibus nulla, vel mattis lacus justo at ante. Sed ut mollis ex, sed tincidunt ex.\n\nMauris laoreet nibh eget erat tincidunt pharetra. Aenean sagittis maximus consectetur. Curabitur interdum nibh sed tincidunt finibus. Sed blandit nec lorem at iaculis. Morbi non augue nec tortor hendrerit mollis ut non arcu. Suspendisse maximus nec ligula a efficitur. Etiam ultrices rhoncus leo quis dapibus. Integer vel rhoncus est. Integer blandit varius auctor. Vestibulum suscipit suscipit risus, sit amet venenatis lacus iaculis a. Duis eu turpis sit amet nibh sagittis convallis at quis ligula. Sed eget justo quis risus iaculis lacinia vitae a justo. In hac habitasse platea dictumst. Maecenas euismod et lorem vel viverra.\n\nDonec bibendum nec ipsum in volutpat. Vivamus in elit venenatis, venenatis libero ac, ultrices dolor. Morbi quis odio in neque consequat rutrum. Suspendisse quis sapien id sapien fermentum dignissim. Nam eu est vel risus volutpat mollis sed quis eros. Proin leo nulla, dictum id hendrerit vitae, scelerisque in elit. Proin consectetur sodales arcu ac tristique. Suspendisse ut elementum ligula, at rhoncus mauris. Aliquam lacinia at diam eget mattis. Phasellus quam leo, hendrerit sit amet mi eget, porttitor aliquet velit. Proin turpis ante, consequat in enim nec, tempus consequat magna. Vestibulum fringilla ac turpis nec malesuada. Proin id lectus iaculis, suscipit erat at, volutpat turpis. In quis faucibus elit, ut maximus nibh. Sed egestas egestas dolor.\n\nNulla varius orci quam, id auctor enim ultrices nec. Morbi et tellus ac metus sodales convallis sed vehicula neque. Pellentesque rhoncus mattis massa a bibendum. Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia Curae; Fusce tincidunt nulla non aliquet facilisis. Praesent nisl nisi, finibus id odio sed, consectetur feugiat mauris. Suspendisse sed lacinia ligula. Duis vitae nisl leo. Donec erat arcu, feugiat sit amet sagittis ac, scelerisque nec est. Pellentesque finibus mauris nulla, in maximus sapien pharetra vitae. Sed leo elit, consequat eu aliquam vitae, feugiat ut eros. Pellentesque dictum feugiat odio sed commodo. Lorem ipsum dolor sit amet, consectetur adipiscing elit. Proin neque quam, varius vel libero sit amet, rhoncus sollicitudin ex. In a dui non neque malesuada pellentesque.\n\nProin tincidunt nisl non commodo faucibus. Sed porttitor arcu neque, vitae bibendum sapien placerat nec. Integer eget tristique orci. Class aptent taciti sociosqu ad litora torquent per conubia nostra, per inceptos himenaeos. Donec eu molestie eros. Nunc iaculis rhoncus enim, vel mattis felis fringilla condimentum. Interdum et malesuada fames ac ante ipsum primis in faucibus. Aenean ac augue nulla. Phasellus vitae nulla lobortis, mattis magna ac, gravida ipsum. Aenean ornare non nunc non luctus. Aenean lacinia lectus nec velit finibus egestas vel ut ipsum. Cras hendrerit rhoncus erat, vel maximus nunc.\n\nPraesent quis imperdiet quam. Praesent ligula tellus, consectetur sed lacus eu, malesuada condimentum tellus. Donec et diam hendrerit, dictum diam quis, aliquet purus. Suspendisse pulvinar neque at efficitur iaculis. Nulla erat orci, euismod id velit sed, dictum hendrerit arcu. Nulla aliquam molestie aliquam. Duis et semper nisi, eget commodo arcu. Praesent rhoncus, nulla id sodales eleifend, ante ipsum pellentesque augue, id iaculis sem est vitae est. Phasellus cursus diam a lorem vestibulum sodales. Nullam lacinia tortor vel tellus commodo, sit amet sodales quam malesuada.\n\nNulla tempor lectus vel arcu feugiat, vel dapibus ex dapibus. Maecenas purus justo, aliquet et sem sit amet, tincidunt venenatis dui. Nulla eget purus id sapien elementum rutrum eu vel libero. Cras non accumsan justo posuere.\n\n"
                                                  ;; prevent string interning, just to be sure
                                                  (UUID/randomUUID))}]})
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

(defn assert-no-existing-data! []
  (assert (empty? (db/get-application-events {}))
          "You have existing applications, refusing to continue. An empty database is needed.")
  (assert (empty? (db/get-catalogue-items {}))
          "You have existing catalogue items, refusing to continue. An empty database is needed."))

(defn create-test-data! []
  (assert-no-existing-data!)
  (api-key/add-api-key! 42 "test data with all roles permitted" api-key/+all-roles+)
  (api-key/add-api-key! 43 "test data with only logged-in role permitted" ["logged-in"])
  (create-test-users-and-roles!)
  (let [res1 (create-resource! {:resource-ext-id "urn:nbn:fi:lb-201403262"
                                :organization "nbn"
                                :actor (+fake-users+ :owner)
                                :license-ids []})
        license1 (create-license! {:actor (+fake-users+ :owner)
                                   :license/type :link
                                   :license/organization "nbn"
                                   :license/title {:en "Test license"
                                                   :fi "Testilisenssi"}
                                   :license/link {:en "https://www.apache.org/licenses/LICENSE-2.0"
                                                  :fi "https://www.apache.org/licenses/LICENSE-2.0"}})
        res2 (create-resource! {:resource-ext-id "Extra Data"
                                :organization "nbn"
                                :actor (+fake-users+ :owner)
                                :license-ids [license1]})
        extra-license (create-license! {:actor (+fake-users+ :owner)
                                        :license/type :link
                                        :license/organization "nbn"
                                        :license/title {:en "Extra license"
                                                        :fi "Ylimääräinen lisenssi"}
                                        :license/link {:en "https://www.apache.org/licenses/LICENSE-2.0"
                                                       :fi "https://www.apache.org/licenses/LICENSE-2.0"}})
        attachment-license (create-attachment-license! {:actor (+fake-users+ :owner)
                                                        :license/organization "nbn"})
        res-with-extra-license (create-resource! {:resource-ext-id "urn:nbn:fi:lb-201403263"
                                                  :organization "nbn"
                                                  :actor (+fake-users+ :owner)
                                                  :license-ids [extra-license attachment-license]})
        form (create-all-field-types-example-form! +fake-users+)
        _ (create-archived-form!)
        workflows (create-workflows! +fake-users+)]
    (create-disabled-license! {:actor (+fake-users+ :owner)
                               :license/organization "nbn"})
    (create-catalogue-item! {:title {:en "Master workflow"
                                     :fi "Master-työvuo"}
                             :infourl {:en "http://www.google.com"
                                       :fi "http://www.google.fi"}
                             :resource-id res1
                             :form-id form
                             :organization "nbn"
                             :workflow-id (:master workflows)})
    (create-catalogue-item! {:title {:en "Decider workflow"
                                     :fi "Päättäjätyövuo"}
                             :infourl {:en "http://www.google.com"
                                       :fi "http://www.google.fi"}
                             :resource-id res1
                             :form-id form
                             :organization "nbn"
                             :workflow-id (:decider workflows)})
    (let [catid (create-catalogue-item! {:title {:en "Default workflow"
                                                 :fi "Oletustyövuo"}
                                         :infourl {:en "http://www.google.com"
                                                   :fi "http://www.google.fi"}
                                         :resource-id res1
                                         :form-id form
                                         :organization "nbn"
                                         :workflow-id (:default workflows)})]
      (create-applications! catid +fake-users+))
    (create-catalogue-item! {:title {:en "Dynamic workflow with extra license"
                                     :fi "Dynaaminen työvuo ylimääräisellä lisenssillä"}
                             :resource-id res-with-extra-license
                             :form-id form
                             :organization "nbn"
                             :workflow-id (:default workflows)})
    (let [thlform (create-thl-demo-form! +fake-users+)
          thl-catid (create-catalogue-item! {:title {:en "THL catalogue item"
                                                     :fi "THL katalogi-itemi"}
                                             :resource-id res1
                                             :form-id thlform
                                             :organiztion "thl"
                                             :workflow-id (:default workflows)})]
      (create-member-applications! thl-catid (+fake-users+ :applicant1) (+fake-users+ :approver1) [{:userid (+fake-users+ :applicant2)}]))
    (let [dynamic-disabled (create-catalogue-item! {:title {:en "Dynamic workflow (disabled)"
                                                            :fi "Dynaaminen työvuo (pois käytöstä)"}
                                                    :resource-id res1
                                                    :form-id form
                                                    :organization "nbn"
                                                    :workflow-id (:default workflows)})]
      (create-disabled-applications! dynamic-disabled
                                     (+fake-users+ :applicant2)
                                     (+fake-users+ :approver1))
      (db/set-catalogue-item-enabled! {:id dynamic-disabled :enabled false}))
    (let [dynamic-expired (create-catalogue-item! {:title {:en "Dynamic workflow (expired)"
                                                           :fi "Dynaaminen työvuo (vanhentunut)"}
                                                   :resource-id res1
                                                   :form-id form
                                                   :organization "nbn"
                                                   :workflow-id (:default workflows)})]
      (db/set-catalogue-item-endt! {:id dynamic-expired :end (time/now)}))))

(defn create-demo-data! []
  (assert-no-existing-data!)
  (let [[users user-data] (case (:authentication rems.config/env)
                            :oidc [+oidc-users+ +oidc-user-data+]
                            [+demo-users+ +demo-user-data+])]
    (api-key/add-api-key! 55 "Finna" api-key/+all-roles+)
    (create-users-and-roles! users user-data)
    (let [res1 (create-resource! {:resource-ext-id "urn:nbn:fi:lb-201403262"
                                  :organization "nbn"
                                  :actor (users :owner)})
          license1 (create-license! {:actor (users :owner)
                                     :license/type :link
                                     :license/organization "nbn"
                                     :license/title {:en "Demo license"
                                                     :fi "Demolisenssi"}
                                     :license/link {:en "https://www.apache.org/licenses/LICENSE-2.0"
                                                    :fi "https://www.apache.org/licenses/LICENSE-2.0"}})
          attachment-license (create-attachment-license! {:actor (users :owner)
                                                          :license/organization "nbn"})
          res2 (create-resource! {:resource-ext-id "Extra Data"
                                  :organization "nbn"
                                  :actor (users :owner)
                                  :license-ids [license1 attachment-license]})
          form (create-all-field-types-example-form! users)
          workflows (create-workflows! users)]
      (create-disabled-license! {:actor (users :owner)
                                 :license/organization "nbn"})
      (let [dynamic (create-catalogue-item! {:title {:en "Dynamic workflow"
                                                     :fi "Dynaaminen työvuo"}
                                             :resource-id res1
                                             :form-id form
                                             :organization "nbn"
                                             :workflow-id (:default workflows)})]
        (create-applications! dynamic users))
      (let [thlform (create-thl-demo-form! users)
            thl-catid (create-catalogue-item! {:title {:en "THL catalogue item"
                                                       :fi "THL katalogi-itemi"}
                                               :resource-id res1
                                               :form-id thlform
                                               :organization "thl"
                                               :workflow-id (:default workflows)})]
        (create-member-applications! thl-catid (users :applicant1) (users :approver1) [{:userid (users :applicant2)}]))
      (let [dynamic-disabled (create-catalogue-item! {:title {:en "Dynamic workflow (disabled)"
                                                              :fi "Dynaaminen työvuo (pois käytöstä)"}
                                                      :resource-id res1
                                                      :form-id form
                                                      :organization "nbn"
                                                      :workflow-id (:default workflows)})]
        (create-disabled-applications! dynamic-disabled
                                       (users :applicant2)
                                       (users :approver1))
        (db/set-catalogue-item-enabled! {:id dynamic-disabled :enabled false})))))

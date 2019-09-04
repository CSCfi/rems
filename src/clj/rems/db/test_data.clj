(ns rems.db.test-data
  "Populating the database with nice test data."
  (:require [clj-time.core :as time]
            [medley.core :refer [map-vals]]
            [rems.api.services.catalogue :as catalogue]
            [rems.api.services.licenses :as licenses]
            [rems.api.services.resource :as resource]
            [rems.api.services.workflow :as workflow]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.form :as form]
            [rems.db.roles :as roles]
            [rems.db.users :as users]
            [rems.poller.entitlements :as entitlements-poller]
            [ring.util.http-response :refer [bad-request!]])
  (:import [java.util UUID]))

;;; test data definitions

(def +fake-users+
  {:applicant1 "alice"
   :applicant2 "malice"
   :approver1 "developer"
   :approver2 "bob"
   :owner "owner"
   :reporter "reporter"
   :reviewer "carl"
   :roleless1 "elsa"
   :roleless2 "frank"})

(def +fake-user-data+
  {"developer" {:eppn "developer" :mail "developer@example.com" :commonName "Developer"}
   "alice" {:eppn "alice" :mail "alice@example.com" :commonName "Alice Applicant"}
   "malice" {:eppn "malice" :mail "malice@example.com" :commonName "Malice Applicant" :twinOf "alice" :other "Attribute Value"}
   "bob" {:eppn "bob" :mail "bob@example.com" :commonName "Bob Approver"}
   "carl" {:eppn "carl" :mail "carl@example.com" :commonName "Carl Reviewer"}
   "elsa" {:eppn "elsa" :mail "elsa@example.com" :commonName "Elsa Roleless"}
   "frank" {:eppn "frank" :mail "frank@example.com" :commonName "Frank Roleless"}
   "owner" {:eppn "owner" :mail "owner@example.com" :commonName "Owner"}
   "reporter" {:eppn "reporter" :mail "reporter@example.com" :commonName "Reporter"}})

(def +demo-users+
  {:applicant1 "RDapplicant1@funet.fi"
   :applicant2 "RDapplicant2@funet.fi"
   :approver1 "RDapprover1@funet.fi"
   :approver2 "RDapprover2@funet.fi"
   :reviewer "RDreview@funet.fi"
   :owner "RDowner@funet.fi"
   :reporter "RDdomainreporter@funet.fi"})

(def +demo-user-data+
  {"RDapplicant1@funet.fi" {:eppn "RDapplicant1@funet.fi" :mail "RDapplicant1.test@test_example.org" :commonName "RDapplicant1 REMSDEMO1"}
   "RDapplicant2@funet.fi" {:eppn "RDapplicant2@funet.fi" :mail "RDapplicant2.test@test_example.org" :commonName "RDapplicant2 REMSDEMO"}
   "RDapprover1@funet.fi" {:eppn "RDapprover1@funet.fi" :mail "RDapprover1.test@rems_example.org" :commonName "RDapprover1 REMSDEMO"}
   "RDapprover2@funet.fi" {:eppn "RDapprover2@funet.fi" :mail "RDapprover2.test@rems_example.org" :commonName "RDapprover2 REMSDEMO"}
   "RDreview@funet.fi" {:eppn "RDreview@funet.fi" :mail "RDreview.test@rems_example.org" :commonName "RDreview REMSDEMO"}
   "RDowner@funet.fi" {:eppn "RDowner@funet.fi" :mail "RDowner.test@test_example.org" :commonName "RDowner REMSDEMO"}
   "RDdomainreporter@funet.fi" {:eppn "RDdomainreporter@funet.fi" :mail "RDdomainreporter.test@test_example.org" :commonName "RDdomainreporter REMSDEMO"}})

;;; helpers for generating test data

(defn command! [command]
  (let [command (merge {:time (time/now)}
                       command)
        result (applications/command! command)]
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

(defn create-user! [user-attributes & roles]
  (let [user (:eppn user-attributes)]
    (users/add-user! user user-attributes)
    (doseq [role roles]
      (roles/add-role! user role))))

(defn create-license! [{:keys [actor]
                        :license/keys [type title link text]
                        :as command}]
  (let [result (licenses/create-license! {:licensetype (name (or type :text))
                                          :localizations
                                          (transpose-localizations {:title title
                                                                    :textcontent (merge link text)})}
                                         (or actor "owner"))]
    (assert (:success result) {:command command :result result})
    (:id result)))

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

(defn create-dynamic-workflow! [{:keys [actor organization title handlers]
                                 :as command}]
  (let [result (workflow/create-workflow!
                {:user-id (or actor "owner")
                 :organization (or organization "abc")
                 :title (or title "")
                 :type :dynamic
                 :handlers
                 (or handlers
                     (do (create-user! (get +fake-user-data+ "developer"))
                         ["developer"]))})]
    (assert (:success result) {:command command :result result})
    (:id result)))

(defn create-catalogue-item! [{:keys [title resource-id form-id workflow-id]
                               :as command}]
  (let [localizations (map-vals (fn [title] {:title title}) title)
        result (catalogue/create-catalogue-item!
                {:resid (or resource-id (create-resource! {}))
                 :form (or form-id (create-form! {}))
                 :wfid (or workflow-id (create-dynamic-workflow! {}))
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

(defn create-users-and-roles! []
  ;; users provided by the fake login
  (let [users (comp +fake-user-data+ +fake-users+)]
    (create-user! (users :applicant1))
    (create-user! (users :applicant2))
    (create-user! (users :approver1))
    (create-user! (users :approver2))
    (create-user! (users :reviewer))
    (create-user! (users :roleless1))
    (create-user! (users :roleless2))
    (create-user! (users :owner) :owner)
    (create-user! (users :reporter) :reporter))
  ;; invalid user for tests
  (db/add-user! {:user "invalid" :userattrs nil}))

(defn- create-demo-users-and-roles! []
  ;; users used on remsdemo
  (let [users (comp +demo-user-data+ +demo-users+)]
    (create-user! (users :applicant1))
    (create-user! (users :applicant2))
    (create-user! (users :approver1))
    (create-user! (users :approver2))
    (create-user! (users :reviewer))
    (create-user! (users :owner) :owner)
    (create-user! (users :reporter) :reporter)))

(defn- create-archived-form! []
  (let [id (create-form! {:actor (+fake-users+ :owner)
                          :form/organization "nbn"
                          :form/title "Archived form, should not be seen by applicants"})]
    (form/set-form-archived! {:id id :archived true})))

(defn- create-disabled-license! [owner]
  (let [id (create-license! {:actor owner
                             :license/type "link"
                             :license/title {:en "Disabled license"
                                             :fi "Käytöstä poistettu lisenssi"}
                             :license/link {:en "http://disabled"
                                            :fi "http://disabled"}})]
    (db/set-license-enabled! {:id id :enabled false})))

(defn- create-basic-form!
  "Creates a bilingual form with all supported field types. Returns id of the form meta."
  [users]
  (create-form!
   {:actor (users :owner)
    :form/organization "nbn"
    :form/title "Basic form"
    :form/fields [;; all form field types
                  {:field/title {:en "Project name"
                                 :fi "Projektin nimi"}
                   :field/optional false
                   :field/type :description
                   :field/placeholder {:en "Project"
                                       :fi "Projekti"}}

                  {:field/title {:en "Here would be some helpful instructions."
                                 :fi "Tässä olisi jotain täyttöohjeita."}
                   :field/optional false
                   :field/type :label}

                  {:field/title {:en "Purpose of the project"
                                 :fi "Projektin tarkoitus"}
                   :field/optional false
                   :field/type :texta
                   :field/placeholder {:en "The purpose of the project is to..."
                                       :fi "Projektin tarkoitus on..."}}

                  {:field/title {:en "Start date of the project"
                                 :fi "Projektin aloituspäivä"}
                   :field/optional true
                   :field/type :date}

                  {:field/title {:en "Project plan"
                                 :fi "Projektisuunnitelma"}
                   :field/optional true
                   :field/type :attachment}

                  {:field/title {:en "Project team size"
                                 :fi "Projektitiimin koko"}
                   :field/optional true
                   :field/type :option
                   :field/options [{:key "1-5"
                                    :label {:en "1-5 persons"
                                            :fi "1-5 henkilöä"}}
                                   {:key "6-20"
                                    :label {:en "6-20 persons"
                                            :fi "6-20 henkilöä"}}
                                   {:key "20+"
                                    :label {:en "over 20 persons"
                                            :fi "yli 20 henkilöä"}}]}

                  {:field/title {:en "Where will the data be used?"
                                 :fi "Missä dataa tullaan käyttämään?"}
                   :field/optional true
                   :field/type :multiselect
                   :field/options [{:key "EU"
                                    :label {:en "Inside EU"
                                            :fi "EU:n sisällä"}}
                                   {:key "USA"
                                    :label {:en "Inside USA"
                                            :fi "Yhdysvalloissa"}}
                                   {:key "Other"
                                    :label {:en "Elsewhere"
                                            :fi "Muualla"}}]}

                  ;; fields which support maxlength
                  {:field/title {:en "Project acronym"
                                 :fi "Projektin lyhenne"}
                   :field/optional true
                   :field/type :text
                   :field/max-length 10}

                  {:field/title {:en "Research plan"
                                 :fi "Tutkimussuunnitelma"}
                   :field/optional true
                   :field/type :texta
                   :field/max-length 100}]}))

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
        owner (users :owner)
        dynamic (create-dynamic-workflow! {:actor owner
                                           :organization "nbn"
                                           :title "dynamic workflow"
                                           :handlers [approver1]})]

    ;; attach both kinds of licenses to all workflows
    (let [link (create-license! {:actor owner
                                 :license/type :link
                                 :license/title {:en "CC Attribution 4.0"
                                                 :fi "CC Nimeä 4.0"}
                                 :license/link {:en "https://creativecommons.org/licenses/by/4.0/legalcode"
                                                :fi "https://creativecommons.org/licenses/by/4.0/legalcode.fi"}})
          text (create-license! {:actor owner
                                 :license/type :text
                                 :license/title {:en "General Terms of Use"
                                                 :fi "Yleiset käyttöehdot"}
                                 :license/text {:en (apply str (repeat 10 "License text in English. "))
                                                :fi (apply str (repeat 10 "Suomenkielinen lisenssiteksti. "))}})]
      (doseq [licid [link text]]
        (doseq [wfid [dynamic]]
          (db/create-workflow-license! {:wfid wfid :licid licid}))))

    {:dynamic dynamic}))

(defn- create-resource-license! [resid text owner]
  (let [licid (create-license! {:actor owner
                                :license/type :link
                                :license/title {:en (str text " (en)")
                                                :fi (str text " (fi)")}
                                :license/link {:en "https://www.apache.org/licenses/LICENSE-2.0"
                                               :fi "https://www.apache.org/licenses/LICENSE-2.0"}})]
    (db/create-resource-license! {:resid resid :licid licid})
    licid))

(defn- create-disabled-applications! [catid applicant approver]
  (create-draft! applicant [catid] "draft with disabled item")

  (let [appid1 (create-draft! applicant [catid] "approved application with disabled item")]
    (command! {:type :application.command/submit
               :application-id appid1
               :actor applicant}))

  (let [appid2 (create-draft! applicant [catid] "submitted application with disabled item")]
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
      (command! {:type :application.command/request-comment
                 :application-id app-id
                 :actor approver
                 :commenters [reviewer]
                 :comment "please have a look"})
      (command! {:type :application.command/comment
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
      (command! {:type :application.command/request-comment
                 :application-id app-id
                 :actor approver
                 :commenters [reviewer]
                 :comment "please have a look"})
      (command! {:type :application.command/comment
                 :application-id app-id
                 :actor reviewer
                 :comment "looking good"})
      (command! {:type :application.command/approve
                 :application-id app-id
                 :actor approver
                 :comment "Thank you! Approved!"})
      (entitlements-poller/run)
      (command! {:type :application.command/close
                 :application-id app-id
                 :actor approver
                 :comment "Research project complete, closing."}))

    (let [app-id (create-draft! applicant [catid] "waiting for comment")]
      (command! {:type :application.command/submit
                 :application-id app-id
                 :actor applicant})
      (command! {:type :application.command/request-comment
                 :application-id app-id
                 :actor approver
                 :commenters [reviewer]
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

(defn create-performance-test-data! []
  (let [resource-count 1000
        application-count 1000
        user-count 1000
        handlers [(+fake-users+ :approver1)
                  (+fake-users+ :approver2)]
        owner (+fake-users+ :owner)
        workflow-id (create-dynamic-workflow! {:actor owner
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
                                     :license/title {:en "Performance License"
                                                     :fi "Suorituskykylisenssi"}
                                     :license/text {:en "Be fast."
                                                    :fi "Ole nopea."}})
        cat-item-ids (vec (for [index (range resource-count)]
                            (let [resource-id (create-resource! {:organization "perf"
                                                                 :license-ids [license-id]})]
                              (create-catalogue-item! {:title {:en (str "Performance test resource " (inc index))
                                                               :fi (str "Suorituskykytestiresurssi " (inc index))}
                                                       :resource-id resource-id
                                                       :form-id form-id
                                                       :workflow-id workflow-id}))))
        user-ids (vec (for [n (range 1 (inc user-count))]
                        (let [user-id (str "perftester" n)]
                          (users/add-user! user-id {:eppn user-id
                                                    :mail (str user-id "@example.com")
                                                    :commonName (str "Performance Tester " n)})
                          user-id)))]
    (dotimes [_ application-count]
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
        (command! {:type :application.command/submit
                   :application-id app-id
                   :actor user-id})
        (command! {:type :application.command/approve
                   :application-id app-id
                   :actor handler
                   :comment ""})))))

(defn create-test-data! []
  (db/add-api-key! {:apikey 42 :comment "test data"})
  (create-users-and-roles!)
  (let [res1 (create-resource! {:resource-ext-id "urn:nbn:fi:lb-201403262"
                                :organization "nbn"
                                :actor (+fake-users+ :owner)
                                :license-ids []})
        res2 (create-resource! {:resource-ext-id "Extra Data"
                                :organization "nbn"
                                :actor (+fake-users+ :owner)
                                :license-ids []})
        _ (create-resource-license! res2 "Some test license" (+fake-users+ :owner))
        res-with-extra-license (create-resource! {:resource-ext-id "urn:nbn:fi:lb-201403263"
                                                  :organization "nbn"
                                                  :actor (+fake-users+ :owner)})
        _ (create-resource-license! res-with-extra-license "Extra license" (+fake-users+ :owner))
        _ (db/create-resource! {:resid "Expired Resource, should not be seen"
                                :organization "nbn"
                                :owneruserid (+fake-users+ :owner)
                                :modifieruserid (+fake-users+ :owner)
                                :end (time/minus (time/now) (time/years 1))})
        form (create-basic-form! +fake-users+)
        _ (create-archived-form!)
        workflows (create-workflows! +fake-users+)]
    (create-disabled-license! (+fake-users+ :owner))
    (let [dynamic (create-catalogue-item! {:title {:en "Dynamic workflow"
                                                   :fi "Dynaaminen työvuo"}
                                           :resource-id res1
                                           :form-id form
                                           :workflow-id (:dynamic workflows)})]
      (create-applications! dynamic +fake-users+))
    (create-catalogue-item! {:title {:en "Dynamic workflow with extra license"
                                     :fi "Dynaaminen työvuo ylimääräisellä lisenssillä"}
                             :resource-id res-with-extra-license
                             :form-id form
                             :workflow-id (:dynamic workflows)})
    (let [thlform (create-thl-demo-form! +fake-users+)
          thl-catid (create-catalogue-item! {:title {:en "THL catalogue item"
                                                     :fi "THL katalogi-itemi"}
                                             :resource-id res1
                                             :form-id thlform
                                             :workflow-id (:dynamic workflows)})]
      (create-member-applications! thl-catid (+fake-users+ :applicant1) (+fake-users+ :approver1) [{:userid (+fake-users+ :applicant2)}]))
    (let [dynamic-disabled (create-catalogue-item! {:title {:en "Dynamic workflow (disabled)"
                                                            :fi "Dynaaminen työvuo (pois käytöstä)"}
                                                    :resource-id res1
                                                    :form-id form
                                                    :workflow-id (:dynamic workflows)})]
      (create-disabled-applications! dynamic-disabled
                                     (+fake-users+ :approver1) ; TODO: this should probably be :applicant1
                                     (+fake-users+ :approver1))
      (db/set-catalogue-item-enabled! {:id dynamic-disabled :enabled false}))
    (let [dynamic-expired (create-catalogue-item! {:title {:en "Dynamic workflow (expired)"
                                                           :fi "Dynaaminen työvuo (vanhentunut)"}
                                                   :resource-id res1
                                                   :form-id form
                                                   :workflow-id (:dynamic workflows)})]
      (db/set-catalogue-item-endt! {:id dynamic-expired :end (time/now)}))))

(defn create-demo-data! []
  (db/add-api-key! {:apikey 55 :comment "Finna"})
  (create-demo-users-and-roles!)
  (let [res1 (create-resource! {:resource-ext-id "urn:nbn:fi:lb-201403262"
                                :organization "nbn"
                                :actor (+demo-users+ :owner)})
        res2 (create-resource! {:resource-ext-id "Extra Data"
                                :organization "nbn"
                                :actor (+demo-users+ :owner)})
        _ (create-resource-license! res2 "Some demo license" (+demo-users+ :owner))
        form (create-basic-form! +demo-users+)
        workflows (create-workflows! +demo-users+)]
    (create-disabled-license! (+demo-users+ :owner))
    (let [dynamic (create-catalogue-item! {:title {:en "Dynamic workflow"
                                                   :fi "Dynaaminen työvuo"}
                                           :resource-id res1
                                           :form-id form
                                           :workflow-id (:dynamic workflows)})]
      (create-applications! dynamic +demo-users+))
    (let [thlform (create-thl-demo-form! +demo-users+)
          thl-catid (create-catalogue-item! {:title {:en "THL catalogue item"
                                                     :fi "THL katalogi-itemi"}
                                             :resource-id res1
                                             :form-id thlform
                                             :workflow-id (:dynamic workflows)})]
      (create-member-applications! thl-catid (+demo-users+ :applicant1) (+demo-users+ :approver1) [{:userid (+demo-users+ :applicant2)}]))
    (let [dynamic-disabled (create-catalogue-item! {:title {:en "Dynamic workflow (disabled)"
                                                            :fi "Dynaaminen työvuo (pois käytöstä)"}
                                                    :resource-id res1
                                                    :form-id form
                                                    :workflow-id (:dynamic workflows)})]
      (create-disabled-applications! dynamic-disabled
                                     (+demo-users+ :approver1) ; TODO: this should probably be :applicant1
                                     (+demo-users+ :approver1))
      (db/set-catalogue-item-enabled! {:id dynamic-disabled :enabled false}))))

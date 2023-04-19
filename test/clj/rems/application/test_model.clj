(ns rems.application.test-model
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [hiccup.core :as hiccup]
            [medley.core :refer [map-vals]]
            [rems.api.schema :as schema]
            [rems.application.events :as events]
            [rems.application.model :as model]
            [rems.common.util :refer [deep-merge]]
            [rems.permissions :as permissions]
            [schema.core :as s])
  (:import [java.util UUID]
           [org.joda.time DateTime]))

;;;; Mock injections

(def ^:private get-form-template
  {40 {:form/id 40
       :organization {:organization/id "org" :organization/name {:fi "Organisaatio" :en "Organisation"} :organization/short-name {:fi "ORG" :en "ORG"}}
       :form/internal-name "form name"
       :form/external-title {:en "en form title"
                             :fi "fi form title"}
       :form/fields [{:field/id "41"
                      :field/title {:en "en title" :fi "fi title"}
                      :field/placeholder {:en "en placeholder" :fi "fi placeholder"}
                      :field/optional false
                      :field/options []
                      :field/max-length 100
                      :field/type :description}
                     {:field/id "42"
                      :field/title {:en "en title" :fi "fi title"}
                      :field/placeholder {:en "en placeholder" :fi "fi placeholder"}
                      :field/optional false
                      :field/options []
                      :field/max-length 100
                      :field/type :text}
                     {:field/id "43"
                      :field/title {:en "en title" :fi "fi title"}
                      :field/placeholder {:en "en placeholder" :fi "fi placeholder"}
                      :field/optional true
                      :field/type :text
                      :field/privacy :private}]
       :enabled true
       :archived false}
   41 {:form/id 41
       :organization {:organization/id "org" :organization/name {:fi "Organisaatio" :en "Organization"} :organization/short-name {:fi "ORG" :en "ORG"}}
       :form/internal-name "form name 2"
       :form/external-title {:en "en form title 2"
                             :fi "fi form title 2"}
       :form/fields [{:field/id "41"
                      :field/title {:en "en title" :fi "fi title"}
                      :field/placeholder {:en "en placeholder" :fi "fi placeholder"}
                      :field/optional false
                      :field/max-length 100
                      :field/type :text}]
       :enabled true
       :archived false}})

(def ^:private get-catalogue-item
  {10 {:id 10
       :resource-id 11
       :resid "urn:11"
       :wfid 50
       :formid 40
       :organization {:organization/id "org" :organization/name {:fi "Organisaatio" :en "Organization"} :organization/short-name {:fi "ORG" :en "ORG"}}
       :localizations {:en {:id 10
                            :langcode :en
                            :title "en title"
                            :infourl "http://info.com"}
                       :fi {:id 10
                            :langcode :fi
                            :title "fi title"
                            :infourl nil}}
       :start (DateTime. 100)
       :end nil
       :enabled true
       :archived false
       :expired false}
   20 {:id 20
       :resource-id 21
       :resid "urn:21"
       :wfid 50
       :formid 40
       :organization {:organization/id "org" :organization/name {:fi "Organisaatio" :en "Organization"} :organization/short-name {:fi "ORG" :en "ORG"}}
       :localizations {:en {:id 20
                            :langcode :en
                            :title "en title"
                            :infourl "http://info.com"}
                       :fi {:id 20
                            :langcode :fi
                            :title "fi title"
                            :infourl nil}}
       :start (DateTime. 100)
       :end nil
       :enabled true
       :archived false
       :expired false}
   30 {:id 30
       :resource-id 31
       :resid "urn:31"
       :wfid 50
       :formid 40
       :organization {:organization/id "org" :organization/name {:fi "Organisaatio" :en "Organization"} :organization/short-name {:fi "ORG" :en "ORG"}}
       :localizations {:en {:id 20
                            :langcode :en
                            :title "en title"
                            :infourl "http://info.com"}
                       :fi {:id 20
                            :langcode :fi
                            :title "fi title"
                            :infourl nil}}
       :start (DateTime. 100)
       :end nil
       :enabled true
       :archived false
       :expired false}
   40 {:id 40
       :resource-id 31
       :resid "urn:31"
       :wfid 50
       :formid 41
       :organization {:organization/id "org" :organization/name {:fi "Organisaatio" :en "Organization"} :organization/short-name {:fi "ORG" :en "ORG"}}
       :localizations {:en {:id 20
                            :langcode :en
                            :title "en title"
                            :infourl "http://info.com"}
                       :fi {:id 20
                            :langcode :fi
                            :title "fi title"
                            :infourl nil}}
       :start (DateTime. 100)
       :end nil
       :enabled true
       :archived false
       :expired false}})

(def ^:private get-config
  (constantly {:application-deadline-days 1}))

(def ^:private get-license
  {30 {:id 30
       :organization {:organization/id "org" :organization/name {:fi "Organisaatio" :en "Organization"} :organization/short-name {:fi "ORG" :en "ORG"}}
       :licensetype "link"
       :localizations {:en {:title "en title"
                            :textcontent "http://en-license-link"}
                       :fi {:title "fi title"
                            :textcontent "http://fi-license-link"}}
       :enabled true
       :archived false}
   31 {:id 31
       :organization {:organization/id "org" :organization/name {:fi "Organisaatio" :en "Organization"} :organization/short-name {:fi "ORG" :en "ORG"}}
       :licensetype "text"
       :localizations {:en {:title "en title"
                            :textcontent "en license text"}
                       :fi {:title "fi title"
                            :textcontent "fi license text"}}
       :enabled true
       :archived false}
   32 {:id 32
       :organization {:organization/id "org" :organization/name {:fi "Organisaatio" :en "Organization"} :organization/short-name {:fi "ORG" :en "ORG"}}
       :licensetype "attachment"
       :localizations {:en {:title "en title"
                            :textcontent "en filename"
                            :attachment-id 3201}
                       :fi {:title "fi title"
                            :textcontent "fi filename"
                            :attachment-id 3202}}
       :enabled true
       :archived false}
   33 {:id 33
       :organization {:organization/id "org" :organization/name {:fi "Organisaatio" :en "Organization"} :organization/short-name {:fi "ORG" :en "ORG"}}
       :licensetype "attachment"
       :localizations {:en {:title "en title"
                            :textcontent "en filename"
                            :attachment-id 3301}
                       :fi {:title "fi title"
                            :textcontent "fi filename"
                            :attachment-id 3302}}
       :enabled true
       :archived false}
   34 {:id 34
       :organization {:organization/id "org" :organization/name {:fi "Organisaatio" :en "Organization"} :organization/short-name {:fi "ORG" :en "ORG"}}
       :licensetype "attachment"
       :localizations {:en {:title "en title"
                            :textcontent "en filename"
                            :attachment-id 3401}
                       :fi {:title "fi title"
                            :textcontent "fi filename"
                            :attachment-id 3402}}
       :enabled true
       :archived false}})

(def ^:private get-user
  {"applicant" {:userid "applicant"
                :email "applicant@example.com"
                :name "Applicant"
                :secret "secret"}
   "reviewer" {:userid "reviewer"
               :email "reviewer@example.com"
               :name "Reviewer"
               :secret "secret"}
   "decider" {:userid "decider"
              :email "decider@example.com"
              :name "Decider"
              :secret "secret"}
   "handler" {:userid "handler"
              :email "handler@example.com"
              :name "Handler"
              :secret "secret"}
   "member" {:userid "member"
             :email "member@example.com"
             :name "Member"
             :secret "secret"}})

(def ^:private get-users-with-role
  {:owner ["owner1"]
   :reporter ["reporter1"]})

(def ^:private get-workflow
  {50 {:id 50
       :organization {:organization/id "org" :organization/name {:fi "Organisaatio" :en "Organization"} :organization/short-name {:fi "ORG" :en "ORG"}}
       :title "workflow title"
       :workflow {:type "workflow/dynamic"
                  :handlers [(get-user "handler")]
                  :licenses []}
       :enabled true
       :archived false}})

;; XXX: no attachments here for now
(defn ^:private get-attachments-for-application [id]
  [])

(defn blacklisted? [user resource]
  (contains? #{["applicant" "urn:11"]
               ["applicant" "urn:31"]
               ["member" "urn:11"]}
             [user resource]))

(def injections {:blacklisted? blacklisted?
                 :get-form-template get-form-template
                 :get-catalogue-item get-catalogue-item
                 :get-config get-config
                 :get-license get-license
                 :get-user get-user
                 :get-users-with-role get-users-with-role
                 :get-workflow get-workflow
                 :get-attachments-for-application get-attachments-for-application})

(deftest test-dummies-schema
  (doseq [[description schema dummies] [["form template" schema/FormTemplate get-form-template]
                                        ["catalogue item" schema/CatalogueItem get-catalogue-item]
                                        ["license" schema/License get-license]
                                        ["workflow" schema/Workflow get-workflow]]]
    (doseq [[id dummy] dummies]
      (testing (str description " " id)
        (is (s/validate schema dummy))))))

;;;; Collecting sample applications

(def ^:dynamic *sample-event-seqs*)

(defn save-sample-events! [events]
  (when (bound? #'*sample-event-seqs*)
    (swap! *sample-event-seqs* conj events))
  events)

(defn state-role-permissions [application]
  (->> (:application/role-permissions application)
       (map (fn [[role permissions]]
              {:state (:application/state application)
               :role role
               :permissions permissions}))
       (sort-by :role)))

(deftest test-state-role-permissions
  (is (= [{:state :application.state/submitted
           :role :role-1
           :permissions #{:foo}}
          {:state :application.state/submitted
           :role :role-2
           :permissions #{:bar :gazonk}}]
         (state-role-permissions
          (-> {:application/state :application.state/submitted}
              (permissions/update-role-permissions {:role-1 #{:foo}
                                                    :role-2 #{:bar :gazonk}}))))))

(defn summarize-permissions [state-role-permissions]
  (let [states (->> state-role-permissions (map :state) distinct sort)
        roles (->> state-role-permissions (map :role) distinct sort)]
    (for [state states
          role roles]
      (let [perm-sets (->> state-role-permissions
                           (filter #(= state (:state %)))
                           (filter #(= role (:role %)))
                           (map :permissions))
            all-perms (apply set/union perm-sets)
            ;; the states and permissions are not guaranteed to have a 1:1 mapping,
            ;; so we separate conditional permissions from those that are always there
            always-perms (if (empty? perm-sets)
                           #{}
                           (apply set/intersection perm-sets))
            sometimes-perms (set/difference all-perms always-perms)]
        {:state state
         :role role
         :always-perms always-perms
         :sometimes-perms sometimes-perms}))))

(deftest test-summarize-permissions
  (testing "always same permissions"
    (is (= [{:state :application.state/submitted
             :role :role-1
             :always-perms #{:foo}
             :sometimes-perms #{}}]
           (summarize-permissions
            [{:state :application.state/submitted
              :role :role-1
              :permissions #{:foo}}]))))

  (testing "sometimes different permissions"
    (is (= [{:state :application.state/submitted
             :role :role-1
             :always-perms #{}
             :sometimes-perms #{:bar :foo}}]
           (summarize-permissions
            [{:state :application.state/submitted
              :role :role-1
              :permissions #{:foo}}
             {:state :application.state/submitted
              :role :role-1
              :permissions #{:bar}}]))))

  (testing "fills out missing state-role combinations"
    (is (= [{:state :application.state/draft
             :role :role-1
             :always-perms #{}
             :sometimes-perms #{}}
            {:state :application.state/draft
             :role :role-2
             :always-perms #{:bar :gazonk}
             :sometimes-perms #{}}
            {:state :application.state/submitted
             :role :role-1
             :always-perms #{:foo}
             :sometimes-perms #{}}
            {:state :application.state/submitted
             :role :role-2
             :always-perms #{}
             :sometimes-perms #{}}]
           (summarize-permissions
            [{:state :application.state/submitted
              :role :role-1
              :permissions #{:foo}}
             {:state :application.state/draft
              :role :role-2
              :permissions #{:bar :gazonk}}])))))

(defn permissions-reference-doc [summary]
  (let [states (->> summary (map :state) distinct sort)
        roles (->> summary (map :role) distinct sort)
        perms-by-state-and-role (->> (group-by (juxt :state :role) summary)
                                     (map-vals first))
        nowrap (fn [s]
                 ;; GitHub will strip all CSS from markdown, so we cannot use CSS for nowrap
                 (-> s
                     (str/replace " " "\u00A0") ;  non-breaking space
                     (str/replace "-" "\u2011")))] ; non-breaking hyphen
    (hiccup/html
     [:table {:border 1}
      [:tr
       [:th (nowrap "State \\ Role")]
       (for [role roles]
         [:th (nowrap (name role))])]
      (for [state states]
        [:tr
         [:th {:valign :top}
          (nowrap (name state))]
         (for [role roles]
           (let [{:keys [always-perms sometimes-perms]} (get perms-by-state-and-role [state role])]
             [:td {:valign :top}
              "<!-- role: " (name role) " -->"
              (for [perm (sort always-perms)]
                [:div (nowrap (name perm))])
              (for [perm (sort sometimes-perms)]
                [:div [:i "(" (nowrap (name perm)) ")"]])]))])])))

(defn output-permissions-reference [event-seqs]
  (let [applications (map (fn [events]
                            (reduce model/application-view nil events))
                          event-seqs)]
    (spit "docs/application-permissions.md"
          (str
           "# Application Permissions Reference\n\n"
           (permissions-reference-doc
            (summarize-permissions
             (mapcat state-role-permissions applications)))))))

(defn permissions-reference-fixture [f]
  (binding [*sample-event-seqs* (atom [])]
    (f)
    (output-permissions-reference @*sample-event-seqs*)))

(use-fixtures :once permissions-reference-fixture)

;;;; Utilities

(defn apply-events [events]
  (let [application (->> events
                         events/validate-events
                         save-sample-events!
                         (reduce model/application-view nil)
                         ;; permissions are tested separately in rems.application.test-master-workflow
                         permissions/cleanup)]
    (is (contains? model/states (:application/state application)))
    application))

(defn recreate
  "Use (is (= app (recreate app))) instead of (is (= app (apply-events (:application/events app))))"
  [application]
  (apply-events (:application/events application)))

;;;; Tests for application-view

;;; Start by defining some useful states

(def created-event {:event/type :application.event/created
                    :event/time (DateTime. 1000)
                    :event/actor "applicant"
                    :application/id 1
                    :application/external-id "extid"
                    :application/resources [{:catalogue-item/id 10
                                             :resource/ext-id "urn:11"}
                                            {:catalogue-item/id 20
                                             :resource/ext-id "urn:21"}]
                    :application/licenses [{:license/id 30}
                                           {:license/id 31}
                                           {:license/id 32}]
                    :application/forms [{:form/id 40}]
                    :workflow/id 50
                    :workflow/type :workflow/master})

(def created-application {:application/id 1
                          :application/external-id "extid"
                          :application/generated-external-id "extid"
                          :application/state :application.state/draft
                          :application/todo nil
                          :application/created (DateTime. 1000)
                          :application/modified (DateTime. 1000)
                          :application/last-activity (DateTime. 1000)
                          :application/applicant {:userid "applicant"}
                          :application/members #{}
                          :application/past-members #{}
                          :application/invitation-tokens {}
                          :application/resources [{:catalogue-item/id 10
                                                   :resource/ext-id "urn:11"}
                                                  {:catalogue-item/id 20
                                                   :resource/ext-id "urn:21"}]
                          :application/licenses [{:license/id 30}
                                                 {:license/id 31}
                                                 {:license/id 32}]
                          :application/accepted-licenses {}
                          :application/events [created-event]
                          :application/forms [{:form/id 40}]
                          :application/workflow {:workflow/type :workflow/master
                                                 :workflow/id 50}
                          :application/attachments []})

(deftest test-application-view-created
  (is (= created-application (recreate created-application))))

(def saved-event {:event/type :application.event/draft-saved
                  :event/time (DateTime. 2000)
                  :event/actor "applicant"
                  :application/id 1
                  :application/field-values [{:form 40 :field "41" :value "foo"}
                                             {:form 40 :field "42" :value "bar"}
                                             {:form 40 :field "43" :value "private answer"}
                                             {:form 40 :field "field-does-not-exist" :value "something"}]})

(def saved-application (merge created-application
                              {:application/modified (DateTime. 2000)
                               :application/last-activity (DateTime. 2000)
                               :application/events [created-event saved-event]
                               :application/accepted-licenses {}
                               :rems.application.model/draft-answers [{:form 40 :field "41" :value "foo"}
                                                                      {:form 40 :field "42" :value "bar"}
                                                                      {:form 40 :field "43" :value "private answer"}
                                                                      {:form 40 :field "field-does-not-exist" :value "something"}]}))

(deftest test-application-view-saved
  (is (= saved-application (recreate saved-application))))


(def licenses-accepted-event {:event/type :application.event/licenses-accepted
                              :event/time (DateTime. 2500)
                              :event/actor "applicant"
                              :application/id 1
                              :application/accepted-licenses #{30 31 32}})

(def licenses-accepted-application (merge saved-application
                                          {:application/last-activity (DateTime. 2500)
                                           :application/events [created-event saved-event licenses-accepted-event]
                                           :application/accepted-licenses {"applicant" #{30 31 32}}}))

(deftest test-application-view-licenses-accepted
  (is (= licenses-accepted-application (recreate licenses-accepted-application))))

(def submitted-event {:event/type :application.event/submitted
                      :event/time (DateTime. 3000)
                      :event/actor "applicant"
                      :application/id 1})

(def submitted-application (-> licenses-accepted-application
                               (dissoc :rems.application.model/draft-answers)
                               (merge {:application/last-activity (DateTime. 3000)
                                       :application/events [created-event saved-event licenses-accepted-event submitted-event]
                                       :application/first-submitted (DateTime. 3000)
                                       :application/state :application.state/submitted
                                       :application/todo :new-application
                                       :rems.application.model/submitted-answers [{:form 40 :field "41" :value "foo"}
                                                                                  {:form 40 :field "42" :value "bar"}
                                                                                  {:form 40 :field "43" :value "private answer"}
                                                                                  {:form 40 :field "field-does-not-exist" :value "something"}]
                                       :rems.application.model/previous-submitted-answers nil})))

(deftest test-application-view-submitted
  (is (= submitted-application (recreate submitted-application))))

(deftest test-application-view-external-id-assigned
  (let [event {:event/type :application.event/external-id-assigned
               :event/time (DateTime. 4000)
               :event/actor "handler"
               :application/id 1
               :application/external-id "ext123"}
        application (merge submitted-application
                           {:application/last-activity (DateTime. 4000)
                            :application/events (conj (:application/events submitted-application)
                                                      event)
                            :application/external-id "ext123"
                            :application/assigned-external-id "ext123"})]
    (is (= application (recreate application)))))

(def approved-event {:event/type :application.event/approved
                     :event/time (DateTime. 4000)
                     :event/actor "handler"
                     :application/id 1
                     :application/comment "looks good"})

(def approved-application (merge submitted-application
                                 {:application/last-activity (DateTime. 4000)
                                  :application/events (conj (:application/events submitted-application)
                                                            approved-event)
                                  :application/state :application.state/approved
                                  :application/todo nil}))

(deftest test-application-view-approved
  (is (= approved-application (recreate approved-application))))

;;; Now use the defined states in tests

(deftest test-application-view-licenses-added
  (let [licenses-added-event {:event/type :application.event/licenses-added
                              :event/time (DateTime. 3500)
                              :event/actor "handler"
                              :application/id 1
                              :application/licenses [{:license/id 33}]
                              :application/comment "Please sign these terms also"}
        licenses-added-application (merge submitted-application
                                          {:application/last-activity (DateTime. 3500)
                                           :application/modified (DateTime. 3500)
                                           :application/events (conj (:application/events submitted-application)
                                                                     licenses-added-event)
                                           :application/licenses [{:license/id 33}
                                                                  {:license/id 30}
                                                                  {:license/id 31}
                                                                  {:license/id 32}]})]
    (is (= licenses-added-application (recreate licenses-added-application)))))

(deftest test-application-view-copied
  (testing "copied from"
    (let [new-event {:event/type :application.event/copied-from
                     :event/time (DateTime. 3000)
                     :event/actor "applicant"
                     :application/id 1
                     :application/copied-from {:application/id 42
                                               :application/external-id "2019/42"}}
          events [created-event saved-event new-event]
          expected-application (merge saved-application
                                      {:application/last-activity (DateTime. 3000)
                                       :application/events events
                                       :application/copied-from {:application/id 42
                                                                 :application/external-id "2019/42"}
                                       :rems.application.model/submitted-answers [{:form 40 :field "41" :value "foo"}
                                                                                  {:form 40 :field "42" :value "bar"}
                                                                                  {:form 40 :field "43" :value "private answer"}
                                                                                  {:form 40 :field "field-does-not-exist" :value "something"}]})]
      (is (= expected-application (recreate expected-application)))))

  (testing "copied to"
    (let [;; two copied-to events in order to test the order in which they are shown
          new-event-1 {:event/type :application.event/copied-to
                       :event/time (DateTime. 3000)
                       :event/actor "applicant"
                       :application/id 1
                       :application/copied-to {:application/id 666
                                               :application/external-id "2020/666"}}
          new-event-2 {:event/type :application.event/copied-to
                       :event/time (DateTime. 3000)
                       :event/actor "applicant"
                       :application/id 1
                       :application/copied-to {:application/id 777
                                               :application/external-id "2021/777"}}
          events [created-event saved-event new-event-1 new-event-2]
          expected-application (merge saved-application
                                      {:application/last-activity (DateTime. 3000)
                                       :application/events events
                                       :application/copied-to [{:application/id 666
                                                                :application/external-id "2020/666"}
                                                               {:application/id 777
                                                                :application/external-id "2021/777"}]})]
      (is (= expected-application (recreate expected-application))))))

(def submitted-returned-application
  (let [returned-event {:event/type :application.event/returned
                        :event/time (DateTime. 4000)
                        :event/actor "handler"
                        :application/id 1
                        :application/comment "fix stuff"}]
    (merge submitted-application
           {:application/last-activity (DateTime. 4000)
            :application/events [created-event saved-event licenses-accepted-event submitted-event returned-event]
            :application/state :application.state/returned
            :application/todo nil
            :rems.application.model/draft-answers [{:form 40 :field "41" :value "foo"}
                                                   {:form 40 :field "42" :value "bar"}
                                                   {:form 40 :field "43" :value "private answer"}
                                                   {:form 40 :field "field-does-not-exist" :value "something"}]})))

(def submitted-returned-resubmitted-application
  (let [submitted-event {:event/type :application.event/submitted
                         :event/time (DateTime. 7000)
                         :event/actor "applicant"
                         :application/id 1}
        events (conj (:application/events submitted-returned-application) submitted-event)]
    (-> submitted-returned-application
        (dissoc :rems.application.model/draft-answers)
        (merge {:application/last-activity (DateTime. 7000)
                :application/events events
                :application/state :application.state/submitted
                :application/todo :resubmitted-application
                :rems.application.model/submitted-answers [{:form 40 :field "41" :value "new foo"}
                                                           {:form 40 :field "42" :value "new bar"}
                                                           {:form 40 :field "43" :value "new private answer"}
                                                           {:form 40 :field "field-does-not-exist" :value "something"}]
                :rems.application.model/previous-submitted-answers [{:form 40 :field "41" :value "foo"}
                                                                    {:form 40 :field "42" :value "bar"}
                                                                    {:form 40 :field "43" :value "private answer"}
                                                                    {:form 40 :field "field-does-not-exist" :value "something"}]}))))

(deftest test-application-view-returned-resubmitted
  (testing "> returned"
    (let [expected-application submitted-returned-application
          events (:application/events expected-application)]
      (is (= expected-application (recreate expected-application)))

      (testing "> draft saved x2"
        (let [new-event-1 {:event/type :application.event/draft-saved
                           :event/time (DateTime. 5000)
                           :event/actor "applicant"
                           :application/id 1
                           ;; non-submitted versions should not show up as the previous value
                           :application/field-values [{:form 40 :field "41" :value "intermediate draft"}
                                                      {:form 40 :field "42" :value "intermediate draft"}
                                                      {:form 40 :field "43" :value "intermediate draft"}
                                                      {:form 40 :field "field-does-not-exist" :value "something"}]}
              new-event-2 {:event/type :application.event/draft-saved
                           :event/time (DateTime. 6000)
                           :event/actor "applicant"
                           :application/id 1
                           :application/field-values [{:form 40 :field "41" :value "new foo"}
                                                      {:form 40 :field "42" :value "new bar"}
                                                      {:form 40 :field "43" :value "new private answer"}
                                                      {:form 40 :field "field-does-not-exist" :value "something"}]}
              events (conj events new-event-1 new-event-2)
              expected-application (deep-merge expected-application
                                               {:application/modified (DateTime. 6000)
                                                :application/last-activity (DateTime. 6000)
                                                :application/events events
                                                :rems.application.model/draft-answers [{:form 40 :field "41" :value "new foo"}
                                                                                       {:form 40 :field "42" :value "new bar"}
                                                                                       {:form 40 :field "43" :value "new private answer"}
                                                                                       {:form 40 :field "field-does-not-exist" :value "something"}]})]
          (is (= expected-application (recreate expected-application)))

          (testing "> resubmitted"
            (let [new-event {:event/type :application.event/submitted
                             :event/time (DateTime. 7000)
                             :event/actor "applicant"
                             :application/id 1}
                  events (conj events new-event)
                  expected-application (-> expected-application
                                           (dissoc :rems.application.model/draft-answers)
                                           (merge {:application/last-activity (DateTime. 7000)
                                                   :application/events events
                                                   :application/state :application.state/submitted
                                                   :application/todo :resubmitted-application
                                                   :rems.application.model/submitted-answers [{:form 40 :field "41" :value "new foo"}
                                                                                              {:form 40 :field "42" :value "new bar"}
                                                                                              {:form 40 :field "43" :value "new private answer"}
                                                                                              {:form 40 :field "field-does-not-exist" :value "something"}]
                                                   :rems.application.model/previous-submitted-answers [{:form 40 :field "41" :value "foo"}
                                                                                                       {:form 40 :field "42" :value "bar"}
                                                                                                       {:form 40 :field "43" :value "private answer"}
                                                                                                       {:form 40 :field "field-does-not-exist" :value "something"}]}))]
              (is (= expected-application (recreate expected-application)))))))

      (testing "> resubmitted (no draft saved)"
        (let [new-event {:event/type :application.event/submitted
                         :event/time (DateTime. 7000)
                         :event/actor "applicant"
                         :application/id 1}
              events (conj events new-event)
              expected-application (-> expected-application
                                       (dissoc :rems.application.model/draft-answers)
                                       (merge {:application/last-activity (DateTime. 7000)
                                               :application/events events
                                               :application/state :application.state/submitted
                                               :application/todo :resubmitted-application
                                               ;; when there was no draft-saved event, the current and
                                               ;; previous submitted answers must be the same
                                               :rems.application.model/submitted-answers [{:form 40 :field "41" :value "foo"}
                                                                                          {:form 40 :field "42" :value "bar"}
                                                                                          {:form 40 :field "43" :value "private answer"}
                                                                                          {:form 40 :field "field-does-not-exist" :value "something"}]
                                               :rems.application.model/previous-submitted-answers [{:form 40 :field "41" :value "foo"}
                                                                                                   {:form 40 :field "42" :value "bar"}
                                                                                                   {:form 40 :field "43" :value "private answer"}
                                                                                                   {:form 40 :field "field-does-not-exist" :value "something"}]}))]
          (is (= expected-application (recreate expected-application))))))))

(deftest test-application-view-resources-changed
  (testing "for submitted application"
    (let [new-event {:event/type :application.event/resources-changed
                     :event/time (DateTime. 3400)
                     :event/actor "handler"
                     :application/id 1
                     :application/comment "You should include this resource."
                     :application/forms [{:form/id 40} {:form/id 41}]
                     :application/resources [{:catalogue-item/id 10 :resource/ext-id "urn:11"}
                                             {:catalogue-item/id 20 :resource/ext-id "urn:21"}
                                             {:catalogue-item/id 30 :resource/ext-id "urn:31"}
                                             {:catalogue-item/id 40 :resource/ext-id "urn:31"}]
                     :application/licenses [{:license/id 30}
                                            {:license/id 31}
                                            {:license/id 32}
                                            {:license/id 34}]}
          events (conj (:application/events submitted-application) new-event)
          expected-application (merge submitted-application
                                      {:application/last-activity (DateTime. 3400)
                                       :application/modified (DateTime. 3400)
                                       :application/events events
                                       :application/forms [{:form/id 40} {:form/id 41}]
                                       :application/resources (concat (:application/resources submitted-application)
                                                                      [{:catalogue-item/id 30
                                                                        :resource/ext-id "urn:31"}
                                                                       {:catalogue-item/id 40
                                                                        :resource/ext-id "urn:31"}])
                                       :application/licenses (conj (:application/licenses submitted-application)
                                                                   {:license/id 34})})]
      (is (= expected-application (recreate expected-application)))))
  (testing "for approved application"
    (let [new-event {:event/type :application.event/resources-changed
                     :event/time (DateTime. 4500)
                     :event/actor "handler"
                     :application/id 1
                     :application/comment "I changed the resources"
                     :application/forms [{:form/id 40}]
                     :application/resources [{:catalogue-item/id 10 :resource/ext-id "urn:11"}
                                             {:catalogue-item/id 30 :resource/ext-id "urn:31"}]
                     :application/licenses [{:license/id 30}
                                            {:license/id 31}
                                            {:license/id 32}
                                            {:license/id 34}]}
          events (conj (:application/events approved-application) new-event)
          expected-application (merge approved-application
                                      {:application/last-activity (DateTime. 4500)
                                       :application/modified (DateTime. 4500)
                                       :application/events events
                                       :application/resources [{:catalogue-item/id 10
                                                                :resource/ext-id "urn:11"}
                                                               {:catalogue-item/id 30
                                                                :resource/ext-id "urn:31"}]
                                       :application/licenses [{:license/id 30}
                                                              {:license/id 31}
                                                              {:license/id 32}
                                                              {:license/id 34}]})]
      (is (= expected-application (recreate expected-application))))))

(deftest test-application-view-licenses-accepted
  (let [expected-application approved-application
        events (:application/events approved-application)]
    (testing "> licenses accepted"
      (let [new-event {:event/type :application.event/licenses-accepted
                       :event/time (DateTime. 4500)
                       :event/actor "applicant"
                       :application/id 1
                       :application/accepted-licenses #{30 31 32}}
            events (conj events new-event)
            expected-application (merge expected-application
                                        {:application/last-activity (DateTime. 4500)
                                         :application/events events
                                         :application/accepted-licenses {"applicant" #{30 31 32}}})]
        (is (= expected-application (recreate expected-application)))

        (testing "> member added"
          (let [new-event {:event/type :application.event/member-added
                           :event/time (DateTime. 4600)
                           :event/actor "handler"
                           :application/id 1
                           :application/member {:userid "member"}}
                events (conj events new-event)
                expected-application (merge expected-application
                                            {:application/last-activity (DateTime. 4600)
                                             :application/events events
                                             :application/members #{{:userid "member"}}})]
            (is (= expected-application (recreate expected-application)))
            (testing "> licenses accepted for new member"
              (let [new-event {:event/type :application.event/licenses-accepted
                               :event/time (DateTime. 4700)
                               :event/actor "member"
                               :application/id 1
                               :application/accepted-licenses #{30}}
                    events (conj events new-event)
                    expected-application (merge expected-application
                                                {:application/last-activity (DateTime. 4700)
                                                 :application/events events
                                                 :application/accepted-licenses {"applicant" #{30 31 32}
                                                                                 "member" #{30}}})]
                (is (= expected-application (recreate expected-application)))
                (testing "> licenses accepted overwrites previous"
                  (let [new-event {:event/type :application.event/licenses-accepted
                                   :event/time (DateTime. 4800)
                                   :event/actor "member"
                                   :application/id 1
                                   :application/accepted-licenses #{31 32}}
                        events (conj events new-event)
                        expected-application (merge expected-application
                                                    {:application/last-activity (DateTime. 4800)
                                                     :application/events events
                                                     :application/accepted-licenses {"applicant" #{30 31 32}
                                                                                     "member" #{31 32}}})]
                    (is (= expected-application (recreate expected-application)))))))))))))

(deftest test-application-view-closed
  (let [new-event {:event/type :application.event/closed
                   :event/time (DateTime. 5000)
                   :event/actor "handler"
                   :application/id 1
                   :application/comment "the project is finished"}
        events (conj (:application/events approved-application) new-event)
        expected-application (merge approved-application
                                    {:application/last-activity (DateTime. 5000)
                                     :application/events events
                                     :application/state :application.state/closed
                                     :application/todo nil})]
    (is (= expected-application (recreate expected-application)))))

(deftest test-application-view-revoked
  (let [new-event {:event/type :application.event/revoked
                   :event/time (DateTime. 5000)
                   :event/actor "handler"
                   :application/id 1
                   :application/comment "license terms were violated"}
        events (conj (:application/events approved-application) new-event)
        expected-application (merge approved-application
                                    {:application/last-activity (DateTime. 5000)
                                     :application/events events
                                     :application/state :application.state/revoked
                                     :application/todo nil})]
    (is (= expected-application (recreate expected-application)))))

(deftest test-application-view-rejected
  (testing "> rejected"
    (let [new-event {:event/type :application.event/rejected
                     :event/time (DateTime. 4000)
                     :event/actor "handler"
                     :application/id 1
                     :application/comment "never gonna happen"}
          events (conj (:application/events submitted-application) new-event)
          expected-application (merge submitted-application
                                      {:application/last-activity (DateTime. 4000)
                                       :application/events events
                                       :application/state :application.state/rejected
                                       :application/todo nil})]
      (is (= expected-application (recreate expected-application))))))

(def review-request-id (UUID/fromString "4de6c2b0-bb2e-4745-8f92-bd1d1f1e8298"))

(def review-requested-event {:event/type :application.event/review-requested
                             :event/time (DateTime. 4000)
                             :event/actor "handler"
                             :application/id 1
                             :application/request-id review-request-id
                             :application/reviewers ["reviewer"]
                             :application/comment "please comment"})

(def reviewed-event {:event/type :application.event/reviewed
                     :event/time (DateTime. 5000)
                     :event/actor "reviewer"
                     :application/id 1
                     :application/request-id review-request-id
                     :application/comment "looks good"})

(deftest test-application-view-reviewing
  (testing "> review requested"
    (let [events (conj (:application/events submitted-application) review-requested-event)
          expected-application (deep-merge submitted-application
                                           {:application/last-activity (DateTime. 4000)
                                            :application/events events
                                            :application/todo :waiting-for-review
                                            ::model/latest-review-request-by-user {"reviewer" review-request-id}})]
      (is (= expected-application (recreate expected-application)))

      (testing "> reviewed"
        (let [events (conj events reviewed-event)
              expected-application (merge expected-application
                                          {:application/last-activity (DateTime. 5000)
                                           :application/events events
                                           :application/todo :no-pending-requests
                                           ::model/latest-review-request-by-user {}})]
          (is (= expected-application (recreate expected-application))))))))

(def decision-request-id (UUID/fromString "db9c7fd6-53be-4b04-b15d-a3a8e0a45e49"))

(def decision-requested-event {:event/type :application.event/decision-requested
                               :event/time (DateTime. 4000)
                               :event/actor "handler"
                               :application/id 1
                               :application/request-id decision-request-id
                               :application/deciders ["decider"]
                               :application/comment "please decide"})

(def decided-event {:event/type :application.event/decided
                    :event/time (DateTime. 5000)
                    :event/actor "decider"
                    :application/id 1
                    :application/request-id decision-request-id
                    :application/decision :approved
                    :application/comment "I approve this"})

(deftest test-application-view-deciding
  (testing "> decision requested"
    (let [events (conj (:application/events submitted-application) decision-requested-event)
          expected-application (merge submitted-application
                                      {:application/last-activity (DateTime. 4000)
                                       :application/events events
                                       :application/todo :waiting-for-decision
                                       :rems.application.model/latest-decision-request-by-user {"decider" decision-request-id}})]
      (is (= expected-application (recreate expected-application)))

      (testing "> decided"
        (let [events (conj events decided-event)
              expected-application (merge expected-application
                                          {:application/last-activity (DateTime. 5000)
                                           :application/events events
                                           :application/todo :no-pending-requests
                                           :rems.application.model/latest-decision-request-by-user {}})]
          (is (= expected-application (recreate expected-application))))))))

(deftest test-application-view-adding-and-inviting
  (testing "> member invited"
    (let [token "b187bda7b9da9053a5d8b815b029e4ba"
          new-event {:event/type :application.event/member-invited
                     :event/time (DateTime. 4000)
                     :event/actor "applicant"
                     :application/id 1
                     :application/member {:name "Mr. Member"
                                          :email "member@example.com"}
                     :invitation/token token}
          events (conj (:application/events submitted-application) new-event)
          expected-application (merge submitted-application
                                      {:application/last-activity (DateTime. 4000)
                                       :application/events events
                                       :application/invitation-tokens {token {:event/actor "applicant"
                                                                              :application/member {:name "Mr. Member"
                                                                                                   :email "member@example.com"}}}})]
      (is (= expected-application (recreate expected-application)))

      (testing "> member uninvited"
        (let [new-event {:event/type :application.event/member-uninvited
                         :event/time (DateTime. 5000)
                         :event/actor "applicant"
                         :application/id 1
                         :application/member {:name "Mr. Member"
                                              :email "member@example.com"}
                         :application/comment "he left the project"}
              events (conj events new-event)
              expected-application (merge expected-application
                                          {:application/last-activity (DateTime. 5000)
                                           :application/events events
                                           :application/invitation-tokens {}})]
          (is (= expected-application (recreate expected-application)))))

      (testing "> member joined"
        (let [new-event {:event/type :application.event/member-joined
                         :event/time (DateTime. 5000)
                         :event/actor "member"
                         :application/id 1
                         :invitation/token token}
              events (conj events new-event)
              expected-application (merge expected-application
                                          {:application/last-activity (DateTime. 5000)
                                           :application/events events
                                           :application/members #{{:userid "member"}}
                                           :application/invitation-tokens {}})]
          (is (= expected-application (recreate expected-application)))))))

  (testing "> member added"
    (let [new-event {:event/type :application.event/member-added
                     :event/time (DateTime. 4000)
                     :event/actor "handler"
                     :application/id 1
                     :application/member {:userid "member"}}
          events (conj (:application/events submitted-application) new-event)
          expected-application (merge submitted-application
                                      {:application/last-activity (DateTime. 4000)
                                       :application/events events
                                       :application/members #{{:userid "member"}}})]
      (is (= expected-application (recreate expected-application)))

      (testing "> member removed"
        (let [new-event {:event/type :application.event/member-removed
                         :event/time (DateTime. 5000)
                         :event/actor "applicant"
                         :application/id 1
                         :application/member {:userid "member"}
                         :application/comment "he left the project"}
              events (conj events new-event)
              expected-application (merge expected-application
                                          {:application/last-activity (DateTime. 5000)
                                           :application/events events
                                           :application/members #{}
                                           :application/past-members #{{:userid "member"}}})]
          (is (= expected-application (recreate expected-application))))))))

(deftest test-applicant-change
  (let [application (-> submitted-application
                        (update :application/events into [{:event/type :application.event/member-added
                                                           :event/time (DateTime. 4000)
                                                           :event/actor "handler"
                                                           :application/id 1
                                                           :application/member {:userid "member1"}}
                                                          {:event/type :application.event/member-added
                                                           :event/time (DateTime. 4000)
                                                           :event/actor "handler"
                                                           :application/id 1
                                                           :application/member {:userid "member2"}}])
                        recreate)]
    (testing "> promote member to applicant"
      (let [new-event {:event/type :application.event/applicant-changed
                       :event/time (DateTime. 5000)
                       :event/actor "handler"
                       :application/id 1
                       :application/applicant {:userid "member1"}}
            events (conj (:application/events application) new-event)
            expected-application (merge application
                                        {:application/last-activity (DateTime. 5000)
                                         :application/events events
                                         :application/applicant {:userid "member1"}
                                         :application/members #{{:userid "member2"} {:userid "applicant"}}})]
        (is (= expected-application (recreate expected-application)))
        (testing "> promote original applicant back"
          (let [new-event {:event/type :application.event/applicant-changed
                           :event/time (DateTime. 6000)
                           :event/actor "handler"
                           :application/id 1
                           :application/applicant {:userid "applicant"}}
                events (conj events new-event)
                expected-application (merge expected-application
                                            {:application/last-activity (DateTime. 6000)
                                             :application/events events
                                             :application/applicant {:userid "applicant"}
                                             :application/members #{{:userid "member1"} {:userid "member2"}}})]
            (is (= expected-application (recreate expected-application)))))))
    ;; This isn't currently a planned use case for the event, just
    ;; documenting this possibility.
    (testing "> promote other user to applicant"
      (let [new-event {:event/type :application.event/applicant-changed
                       :event/time (DateTime. 5000)
                       :event/actor "handler"
                       :application/id 1
                       :application/applicant {:userid "usurper"}}
            events (conj (:application/events application) new-event)
            expected-application (merge application
                                        {:application/last-activity (DateTime. 5000)
                                         :application/events events
                                         :application/applicant {:userid "usurper"}
                                         :application/members #{{:userid "member1"} {:userid "member2"} {:userid "applicant"}}})]
        (is (= expected-application (recreate expected-application)))))))

(deftest test-application-view-actor-invitations
  (testing "> reviewer invited"
    (let [token "abcd1234"
          new-event {:event/type :application.event/reviewer-invited
                     :event/time (DateTime. 4000)
                     :event/actor "handler"
                     :application/id 1
                     :application/reviewer {:name "Mr. Reviewer"
                                            :email "reviewer@example.com"}
                     :invitation/token token}
          events (conj (:application/events submitted-application) new-event)
          expected-application (merge submitted-application
                                      {:application/last-activity (DateTime. 4000)
                                       :application/events events
                                       :application/invitation-tokens {token {:event/actor "handler"
                                                                              :application/reviewer {:name "Mr. Reviewer"
                                                                                                     :email "reviewer@example.com"}}}})]
      (is (= expected-application (recreate expected-application)))

      (testing "> reviewer joined"
        (let [new-event {:event/type :application.event/reviewer-joined
                         :event/time (DateTime. 5000)
                         :event/actor "new-reviewer"
                         :application/id 1
                         :application/request-id review-request-id
                         :invitation/token token}
              events (conj events new-event)
              expected-application (merge expected-application
                                          {:application/last-activity (DateTime. 5000)
                                           :application/events events
                                           :application/invitation-tokens {}
                                           :application/todo :waiting-for-review
                                           :rems.application.model/latest-review-request-by-user {"new-reviewer" review-request-id}})]
          (is (= expected-application (recreate expected-application)))))))
  (testing "> decider invited"
    (let [token "abcd1234"
          new-event {:event/type :application.event/decider-invited
                     :event/time (DateTime. 4000)
                     :event/actor "handler"
                     :application/id 1
                     :application/decider {:name "Mr. Decider"
                                           :email "decider@example.com"}
                     :invitation/token token}
          events (conj (:application/events submitted-application) new-event)
          expected-application (merge submitted-application
                                      {:application/last-activity (DateTime. 4000)
                                       :application/events events
                                       :application/invitation-tokens {token {:event/actor "handler"
                                                                              :application/decider {:name "Mr. Decider"
                                                                                                    :email "decider@example.com"}}}})]
      (is (= expected-application (recreate expected-application)))

      (testing "> decider joined"
        (let [new-event {:event/type :application.event/decider-joined
                         :event/time (DateTime. 5000)
                         :event/actor "new-decider"
                         :application/id 1
                         :application/request-id review-request-id
                         :invitation/token token}
              events (conj events new-event)
              expected-application (merge expected-application
                                          {:application/last-activity (DateTime. 5000)
                                           :application/events events
                                           :application/invitation-tokens {}
                                           :application/todo :waiting-for-decision
                                           :rems.application.model/latest-decision-request-by-user {"new-decider" review-request-id}})]
          (is (= expected-application (recreate expected-application))))))))

;;;; Tests for enriching

;;; A regression/gold master test for the entire enriching pipe

(deftest test-enrich-with-injections
  (is (= {:application/id 1
          :application/external-id "extid"
          :application/generated-external-id "extid"
          :application/state :application.state/approved
          :application/todo nil
          :application/created (DateTime. 1000)
          :application/modified (DateTime. 2000)
          :application/first-submitted (DateTime. 3000)
          :application/last-activity (DateTime. 4000)
          :application/deadline (.plusDays (DateTime. 3000) 1)
          :application/applicant {:userid "applicant"
                                  :email "applicant@example.com"
                                  :name "Applicant"
                                  :secret "secret"}
          :application/members #{}
          :application/past-members #{}
          :application/invitation-tokens {}
          :application/blacklist [{:blacklist/user {:userid "applicant"
                                                    :email "applicant@example.com"
                                                    :name "Applicant"
                                                    :secret "secret"}
                                   :blacklist/resource {:resource/ext-id "urn:11"}}]
          :application/resources [{:catalogue-item/id 10
                                   :resource/id 11
                                   :resource/ext-id "urn:11"
                                   :catalogue-item/title {:en "en title"
                                                          :fi "fi title"}
                                   :catalogue-item/infourl {:en "http://info.com"}
                                   :catalogue-item/start (DateTime. 100)
                                   :catalogue-item/end nil
                                   :catalogue-item/enabled true
                                   :catalogue-item/expired false
                                   :catalogue-item/archived false}
                                  {:catalogue-item/id 20
                                   :resource/id 21
                                   :resource/ext-id "urn:21"
                                   :catalogue-item/title {:en "en title"
                                                          :fi "fi title"}
                                   :catalogue-item/infourl {:en "http://info.com"}
                                   :catalogue-item/start (DateTime. 100)
                                   :catalogue-item/end nil
                                   :catalogue-item/enabled true
                                   :catalogue-item/expired false
                                   :catalogue-item/archived false}]
          :application/licenses [{:license/id 30
                                  :license/type :link
                                  :license/title {:en "en title"
                                                  :fi "fi title"}
                                  :license/link {:en "http://en-license-link"
                                                 :fi "http://fi-license-link"}
                                  :license/enabled true
                                  :license/archived false}
                                 {:license/id 31
                                  :license/type :text
                                  :license/title {:en "en title"
                                                  :fi "fi title"}
                                  :license/text {:en "en license text"
                                                 :fi "fi license text"}
                                  :license/enabled true
                                  :license/archived false}
                                 {:license/id 32
                                  :license/type :attachment
                                  :license/title {:en "en title"
                                                  :fi "fi title"}
                                  :license/attachment-id {:en 3201
                                                          :fi 3202}
                                  :license/attachment-filename {:en "en filename"
                                                                :fi "fi filename"}
                                  :license/enabled true
                                  :license/archived false}]
          :application/accepted-licenses {"applicant" #{30 31 32}}
          :application/events [{:event/type :application.event/created
                                :application/id 1
                                :event/actor "applicant"
                                :application/external-id "extid"
                                :event/actor-attributes {:userid "applicant" :email "applicant@example.com" :name "Applicant" :secret "secret"}
                                :event/time (DateTime. 1000)
                                :application/resources [{:catalogue-item/id 10 :resource/ext-id "urn:11"}
                                                        {:catalogue-item/id 20 :resource/ext-id "urn:21"}]
                                :application/forms [{:form/id 40}]
                                :workflow/id 50
                                :workflow/type :workflow/master
                                :application/licenses [{:license/id 30} {:license/id 31} {:license/id 32}]}
                               {:event/type :application.event/draft-saved
                                :application/id 1
                                :event/time (DateTime. 2000)
                                :event/actor "applicant"
                                :application/field-values [{:form 40 :field "41" :value "foo"}
                                                           {:form 40 :field "42" :value "bar"}
                                                           {:form 40 :field "43" :value "private answer"}
                                                           {:form 40 :field "field-does-not-exist" :value "something"}]
                                :event/actor-attributes {:userid "applicant" :email "applicant@example.com" :name "Applicant" :secret "secret"}}
                               {:event/type :application.event/licenses-accepted
                                :application/id 1
                                :event/time (DateTime. 2500)
                                :event/actor "applicant"
                                :application/accepted-licenses #{30 31 32}
                                :event/actor-attributes {:userid "applicant" :email "applicant@example.com" :name "Applicant" :secret "secret"}}
                               {:event/type :application.event/submitted
                                :application/id 1
                                :event/time (DateTime. 3000)
                                :event/actor "applicant"
                                :event/actor-attributes {:userid "applicant" :email "applicant@example.com" :name "Applicant" :secret "secret"}}
                               {:event/type :application.event/approved
                                :application/id 1
                                :event/time (DateTime. 4000)
                                :event/actor "handler"
                                :application/comment "looks good"
                                :event/actor-attributes {:userid "handler" :email "handler@example.com" :name "Handler" :secret "secret"}}]
          :application/user-roles {"handler" #{:handler}, "reporter1" #{:reporter}}
          :application/role-permissions nil
          :application/description "foo"
          :application/forms [{:form/id 40
                               :form/title "form name" ; deprecated
                               :form/internal-name "form name"
                               :form/external-title {:en "en form title"
                                                     :fi "fi form title"}
                               :form/fields [{:field/id "41"
                                              :field/value "foo"
                                              :field/type :description
                                              :field/title {:en "en title" :fi "fi title"}
                                              :field/placeholder {:en "en placeholder" :fi "fi placeholder"}
                                              :field/optional false
                                              :field/options []
                                              :field/max-length 100
                                              :field/visible true}
                                             {:field/id "42"
                                              :field/value "bar"
                                              :field/type :text
                                              :field/title {:en "en title" :fi "fi title"}
                                              :field/placeholder {:en "en placeholder" :fi "fi placeholder"}
                                              :field/optional false
                                              :field/options []
                                              :field/max-length 100
                                              :field/visible true}
                                             {:field/id "43"
                                              :field/value "private answer"
                                              :field/type :text
                                              :field/title {:en "en title" :fi "fi title"}
                                              :field/placeholder {:en "en placeholder" :fi "fi placeholder"}
                                              :field/optional true
                                              :field/privacy :private
                                              :field/visible true}]}]
          :application/attachments []
          :application/workflow {:workflow/id 50
                                 :workflow/type :workflow/master
                                 :workflow.dynamic/handlers [{:userid "handler"
                                                              :name "Handler"
                                                              :email "handler@example.com"
                                                              :secret "secret"
                                                              :handler/active? true}]}}
         (model/enrich-with-injections approved-application injections))))

(deftest test-enrich-event
  (testing "resources-changed"
    (is (= {:event/type :application.event/resources-changed
            :event/time (DateTime. 1)
            :event/actor "applicant"
            :event/actor-attributes {:userid "applicant" :email "applicant@example.com" :name "Applicant" :secret "secret"}
            :application/id 1
            :application/resources [{:catalogue-item/id 10
                                     :resource/id 11
                                     :resource/ext-id "urn:11"
                                     :catalogue-item/title {:en "en title"
                                                            :fi "fi title"}
                                     :catalogue-item/infourl {:en "http://info.com"}
                                     :catalogue-item/start (DateTime. 100)
                                     :catalogue-item/end nil
                                     :catalogue-item/enabled true
                                     :catalogue-item/expired false
                                     :catalogue-item/archived false}
                                    {:catalogue-item/id 20
                                     :resource/id 21
                                     :resource/ext-id "urn:21"
                                     :catalogue-item/title {:en "en title"
                                                            :fi "fi title"}
                                     :catalogue-item/infourl {:en "http://info.com"}
                                     :catalogue-item/start (DateTime. 100)
                                     :catalogue-item/end nil
                                     :catalogue-item/enabled true
                                     :catalogue-item/expired false
                                     :catalogue-item/archived false}]}
           (model/enrich-event {:event/type :application.event/resources-changed
                                :event/time (DateTime. 1)
                                :event/actor "applicant"
                                :application/id 1
                                :application/resources [{:catalogue-item/id 10 :resource/ext-id "urn:11"}
                                                        {:catalogue-item/id 20 :resource/ext-id "urn:21"}]}
                               get-user get-catalogue-item))))
  (testing "decision-requested"
    (is (= {:event/type :application.event/decision-requested
            :event/time (DateTime. 1)
            :event/actor "handler"
            :event/actor-attributes {:userid "handler" :email "handler@example.com" :name "Handler" :secret "secret"}
            :application/id 1
            :application/deciders [{:userid "decider" :email "decider@example.com" :name "Decider" :secret "secret"}
                                   {:userid "reviewer" :email "reviewer@example.com" :name "Reviewer" :secret "secret"}]}
           (model/enrich-event {:event/type :application.event/decision-requested
                                :event/time (DateTime. 1)
                                :event/actor "handler"
                                :application/id 1
                                :application/deciders ["decider" "reviewer"]}
                               get-user get-catalogue-item))))
  (testing "review-requested"
    (is (= {:event/type :application.event/review-requested
            :event/time (DateTime. 1)
            :event/actor "handler"
            :event/actor-attributes {:userid "handler" :email "handler@example.com" :name "Handler" :secret "secret"}
            :application/id 1
            :application/reviewers [{:userid "decider" :email "decider@example.com" :name "Decider" :secret "secret"}
                                    {:userid "reviewer" :email "reviewer@example.com" :name "Reviewer" :secret "secret"}]}
           (model/enrich-event {:event/type :application.event/review-requested
                                :event/time (DateTime. 1)
                                :event/actor "handler"
                                :application/id 1
                                :application/reviewers ["decider" "reviewer"]}
                               get-user get-catalogue-item))))
  (testing "member-added"
    (is (= {:event/type :application.event/member-added
            :event/time (DateTime. 1)
            :event/actor "handler"
            :event/actor-attributes {:userid "handler" :email "handler@example.com" :name "Handler" :secret "secret"}
            :application/id 1
            :application/member {:userid "member" :email "member@example.com" :name "Member" :secret "secret"}}
           (model/enrich-event {:event/type :application.event/member-added
                                :event/time (DateTime. 1)
                                :event/actor "handler"
                                :application/id 1
                                :application/member {:userid "member"}}
                               get-user get-catalogue-item))))
  (testing "member-removed"
    (is (= {:event/type :application.event/member-removed
            :event/time (DateTime. 1)
            :event/actor "handler"
            :event/actor-attributes {:userid "handler" :email "handler@example.com" :name "Handler" :secret "secret"}
            :application/id 1
            :application/member {:userid "member" :email "member@example.com" :name "Member" :secret "secret"}}
           (model/enrich-event {:event/type :application.event/member-removed
                                :event/time (DateTime. 1)
                                :event/actor "handler"
                                :application/id 1
                                :application/member {:userid "member"}}
                               get-user get-catalogue-item)))))

(deftest test-enrich-answers
  (testing "draft"
    (is (= {:application/forms [{:form/id 1
                                 :form/fields [{:field/id "1" :field/value "a"}
                                               {:field/id "2" :field/value "b"}]}]}
           (model/enrich-answers {:application/forms [{:form/id 1
                                                       :form/fields [{:field/id "1"}
                                                                     {:field/id "2"}]}]
                                  :rems.application.model/draft-answers [{:form 1 :field "1" :value "a"}
                                                                         {:form 1 :field "2" :value "b"}]}))))
  (testing "submitted"
    (is (= {:application/forms [{:form/id 1
                                 :form/fields [{:field/id "1" :field/value "a"}
                                               {:field/id "2" :field/value "b"}]}]}
           (model/enrich-answers {:application/forms [{:form/id 1
                                                       :form/fields [{:field/id "1"}
                                                                     {:field/id "2"}]}]
                                  :rems.application.model/submitted-answers [{:form 1 :field "1" :value "a"}
                                                                             {:form 1 :field "2" :value "b"}]}))))
  (testing "returned"
    (is (= {:application/forms [{:form/id 1
                                 :form/fields [{:field/id "1" :field/value "aa" :field/previous-value "a"}
                                               {:field/id "2" :field/value "bb" :field/previous-value "b"}]}]}
           (model/enrich-answers {:application/forms [{:form/id 1
                                                       :form/fields [{:field/id "1"}
                                                                     {:field/id "2"}]}]
                                  :rems.application.model/submitted-answers [{:form 1 :field "1" :value "a"}
                                                                             {:form 1 :field "2" :value "b"}]
                                  :rems.application.model/draft-answers [{:form 1 :field "1" :value "aa"}
                                                                         {:form 1 :field "2" :value "bb"}]}))))
  (testing "resubmitted"
    (is (= {:application/forms [{:form/id 1
                                 :form/fields [{:field/id "1" :field/value "aa" :field/previous-value "a"}
                                               {:field/id "2" :field/value "bb" :field/previous-value "b"}]}]}
           (model/enrich-answers {:application/forms [{:form/id 1
                                                       :form/fields [{:field/id "1"}
                                                                     {:field/id "2"}]}]
                                  :rems.application.model/previous-submitted-answers [{:form 1 :field "1" :value "a"}
                                                                                      {:form 1 :field "2" :value "b"}]
                                  :rems.application.model/submitted-answers [{:form 1 :field "1" :value "aa"}
                                                                             {:form 1 :field "2" :value "bb"}]})))))

(deftest test-enrich-active-handlers
  (let [application {:application/workflow {:workflow/id 1}
                     :application/events [{:event/actor "applicant"} ; should ignore active non-handlers
                                          {:event/actor "edward"}
                                          {:event/actor "reviewer"}]}
        get-workflow {1 {:workflow {:handlers [{:userid "alphonse" ; should ignore inactive handlers
                                                :name "Alphonse Elric"}
                                               {:userid "edward"
                                                :name "Edward Elric"}]}}}]
    (is (= {:application/workflow {:workflow/id 1
                                   :workflow.dynamic/handlers [{:userid "alphonse"
                                                                :name "Alphonse Elric"}
                                                               {:userid "edward"
                                                                :name "Edward Elric"
                                                                :handler/active? true}]}}
           (-> (model/enrich-workflow-handlers application get-workflow)
               (select-keys [:application/workflow]))))))

(deftest test-enrich-deadline
  (testing "non-submitted application"
    (is (= {:application/created (DateTime. 3000)}
           (model/enrich-deadline {:application/created (DateTime. 3000)}
                                  (constantly {:application-deadline-days 1})))))
  (testing "submitted application, deadline"
    (is (= {:application/created (DateTime. 3000)
            :application/first-submitted (DateTime. 4000)
            :application/deadline (.plusDays (DateTime. 4000) 2)}
           (model/enrich-deadline {:application/created (DateTime. 3000)
                                   :application/first-submitted (DateTime. 4000)}
                                  (constantly {:application-deadline-days 2})))))
  (testing "submitted application, deadline not in use"
    (is (= {:application/created (DateTime. 3000)
            :application/first-submitted (DateTime. 4000)}
           (model/enrich-deadline {:application/created (DateTime. 3000)
                                   :application/first-submitted (DateTime. 4000)}
                                  (constantly {:application-deadline-days nil}))))))

(deftest test-enrich-field-visible
  (let [application {:application/forms [{:form/id 1
                                          :form/fields [{:field/id "fld1"
                                                         :field/title "Option"
                                                         :field/options [{:key "no" :label "No"}
                                                                         {:key "yes" :label "Yes"}]}
                                                        {:field/id "fld2"
                                                         :field/title "Hidden field"
                                                         :field/visibility {:visibility/type :only-if
                                                                            :visibility/field {:field/id "fld1"}
                                                                            :visibility/values ["yes"]}}]}]}
        visible-fields (fn [application]
                         (->> (get-in (model/enrich-field-visible application) [:application/forms 0 :form/fields])
                              (filter :field/visible)
                              (map :field/id)))]
    (is (= ["fld1"] (visible-fields application)) "no answer should not make field visible")
    (is (= ["fld1"] (visible-fields (assoc-in application [:application/forms 0 :form/fields 0 :field/value] "no"))) "other option value should not make field visible")
    (is (= ["fld1" "fld2"] (visible-fields (assoc-in application [:application/forms 0 :form/fields 0 :field/value] "yes"))) "visible when option value is yes")))

(deftest test-hide-sensitive-information
  (let [base (reduce model/application-view nil
                     [created-event
                      submitted-event
                      review-requested-event
                      reviewed-event
                      decision-requested-event
                      decided-event
                      {:event/type :application.event/member-added
                       :event/time (DateTime. 7000)
                       :event/actor "handler"
                       :application/id 1
                       :application/member {:userid "member"}}
                      approved-event])
        enriched (model/enrich-with-injections base injections)
        redacted (#'model/hide-sensitive-information enriched)]
    (testing "blacklist removed"
      (is (seq (:application/blacklist enriched)))
      (is (empty? (:application/blacklist redacted))))
    (testing "handlers removed"
      (is (get-in enriched [:application/workflow :workflow.dynamic/handlers]))
      (is (not (get-in redacted [:application/workflow :workflow.dynamic/handlers]))))
    (testing "events removed"
      (is (= [:application.event/created
              :application.event/submitted
              :application.event/member-added
              :application.event/approved]
             (mapv :event/type (:application/events redacted)))))
    (testing "extra user attributes removed from events"
      (is (:secret (:event/actor-attributes (last (:application/events enriched)))))
      (is (not (:secret (:event/actor-attributes (last (:application/events redacted)))))))
    (testing "extra applicant attributes hidden"
      (is (:secret (:application/applicant enriched)))
      (is (not (:secret (:application/applicant redacted)))))
    (testing "extra member attributes hidden"
      (is (= ["secret"] (mapv :secret (:application/members enriched))))
      (is (= [nil] (mapv :secret (:application/members redacted))))
      (testing "in events"
        (is (:secret (get-in enriched [:application/events 6 :application/member])))
        (is (not (:secret (get-in redacted [:application/events 6 :application/member]))))))
    (testing "no missed extra user attributes"
      (is (not (str/includes? (pr-str redacted) "secret"))))))

(deftest test-apply-user-permissions
  (testing "draft visibility"
    (let [application (-> nil
                          (model/application-view {:event/type :application.event/created
                                                   :event/actor "applicant"
                                                   :workflow/type :workflow/default
                                                   :workflow/id 50})
                          (model/application-view {:event/type :application.event/member-joined
                                                   :event/actor "member"}))
          enriched (model/enrich-with-injections application injections)]
      (testing "sanity check roles"
        (is (= {"applicant" #{:applicant}
                "member" #{:member}
                "handler" #{:handler}
                "reporter1" #{:reporter}} (:application/user-roles enriched))))
      (testing "reporter can't see draft application"
        (is (nil? (model/apply-user-permissions enriched "reporter1"))))
      (testing "handler can't see draft application"
        (is (nil? (model/apply-user-permissions enriched "handler"))))
      (testing "applicant can see draft application"
        (is (model/apply-user-permissions enriched "applicant")))
      (testing "member can see draft application"
        (is (model/apply-user-permissions enriched "member")))))
  (let [application (-> nil
                        (model/application-view {:event/type :application.event/created
                                                 :event/actor "applicant"
                                                 :workflow/type :workflow/default
                                                 :workflow/id 50})
                        (model/application-view {:event/type :application.event/submitted
                                                 :event/actor "applicant"
                                                 :workflow/type :workflow/default
                                                 :workflow/id 50})
                        (permissions/give-role-to-users :handler ["handler"])
                        (permissions/give-role-to-users :reporter ["reporter"])
                        (permissions/give-role-to-users :role-1 ["user-1"])
                        (permissions/give-role-to-users :role-2 ["user-2"])
                        (permissions/update-role-permissions {:role-1 #{}
                                                              :role-2 #{:foo :bar}
                                                              :reporter #{:see-everything}}))
        enriched (model/enrich-with-injections application injections)]

    (testing "users with a role can see the application"
      (is (not (nil? (model/apply-user-permissions enriched "user-1")))))
    (testing "users without a role cannot see the application"
      (is (nil? (model/apply-user-permissions enriched "user-3"))))
    (testing "lists the user's permissions"
      (is (= #{} (:application/permissions (model/apply-user-permissions enriched "user-1"))))
      (is (= #{:foo :bar} (:application/permissions (model/apply-user-permissions enriched "user-2")))))
    (testing "lists the user's roles"
      (is (= #{:role-1} (:application/roles (model/apply-user-permissions enriched "user-1"))))
      (is (= #{:role-2} (:application/roles (model/apply-user-permissions enriched "user-2")))))

    (let [application (-> application
                          (model/application-view {:event/type :application.event/review-requested
                                                   :event/actor "handler"})
                          (model/application-view {:event/type :application.event/remarked
                                                   :application/comment "this is public"
                                                   :event/actor "bob"
                                                   :application/public true})
                          (model/application-view {:event/type :application.event/remarked
                                                   :application/comment "this is private"
                                                   :event/actor "smith"
                                                   :application/public false}))
          enriched (-> application
                       (permissions/update-role-permissions {:role-1 #{:see-everything}})
                       (model/enrich-with-injections injections))]
      (testing "privileged users"
        (let [application (model/apply-user-permissions enriched "user-1")]
          (testing "see all events"
            (is (= [:application.event/created :application.event/submitted :application.event/review-requested
                    :application.event/remarked :application.event/remarked]
                   (mapv :event/type (:application/events application)))))
          (testing "see all applicant attributes"
            (is (= {:userid "applicant" :email "applicant@example.com" :name "Applicant" :secret "secret"}
                   (:application/applicant application))))
          (testing "see dynamic workflow handlers"
            (is (= [{:userid "handler" :email "handler@example.com" :name "Handler" :secret "secret" :handler/active? true}]
                   (get-in application [:application/workflow :workflow.dynamic/handlers]))))))

      (testing "normal users"
        (let [application (model/apply-user-permissions enriched "user-2")]
          (testing "see only some events"
            (is (= [{:event/type :application.event/created}
                    {:event/type :application.event/submitted}
                    {:event/type :application.event/remarked :application/comment "this is public"}]
                   (mapv #(select-keys % [:event/type :application/comment]) (:application/events application)))))
          (testing "see only limited applicant attributes"
            (is (= {:userid "applicant" :email "applicant@example.com" :name "Applicant"}
                   (:application/applicant application))))
          (testing "don't see dynamic workflow handlers"
            (is (= nil
                   (get-in application [:application/workflow :workflow.dynamic/handlers])))))))

    (testing "invitation tokens are not visible to anybody"
      (let [application (-> application
                            (model/application-view {:event/type :application.event/member-invited
                                                     :event/actor "applicant"
                                                     :application/member {:name "member"
                                                                          :email "member@example.com"}
                                                     :invitation/token "secret"})
                            (model/application-view {:event/type :application.event/reviewer-invited
                                                     :event/actor "handler"
                                                     :application/reviewer {:name "new-reviewer"
                                                                            :email "reviewer@example.com"}
                                                     :invitation/token "clandestine"}))
            enriched (model/enrich-with-injections application injections)]
        (testing "- original"
          (is (= #{"secret" "clandestine" nil} (set (map :invitation/token (:application/events enriched)))))
          (is (= {"secret" {:event/actor "applicant"
                            :application/member {:name "member"
                                                 :email "member@example.com"}}
                  "clandestine" {:event/actor "handler"
                                 :application/reviewer {:name "new-reviewer"
                                                        :email "reviewer@example.com"}}}
                 (:application/invitation-tokens enriched)))
          (is (= nil
                 (:application/invited-members enriched))))
        (doseq [user-id ["applicant" "handler"]]
          (testing (str "- as user " user-id)
            (let [limited (model/apply-user-permissions enriched user-id)]
              (is (= #{nil} (set (map :invitation/token (:application/events limited)))))
              (is (= nil
                     (:application/invitation-tokens limited)))
              (is (= #{{:name "member"
                        :email "member@example.com"}}
                     (:application/invited-members limited))))))))

    (let [application (-> application
                          (model/application-view {:event/type :application.event/review-requested
                                                   :event/actor "handler"
                                                   :application/reviewers ["reviewer1"]})
                          (model/enrich-with-injections injections))]
      (testing "personalized waiting for your review"
        (is (= :waiting-for-review
               (:application/todo (model/apply-user-permissions application "handler")))
            "as seen by handler")
        (is (= :waiting-for-your-review
               (:application/todo (model/apply-user-permissions application "reviewer1")))
            "as seen by reviewer"))
      (testing "reviewer sees all applicant attributes"
        (is (= {:userid "applicant" :email "applicant@example.com" :name "Applicant" :secret "secret"}
               (:application/applicant application)))))

    ;; TODO test :application/todo for invited actors

    (let [application (-> application
                          (model/application-view {:event/type :application.event/decision-requested
                                                   :event/actor "handler"
                                                   :application/deciders ["decider1"]})
                          (model/enrich-with-injections injections))]
      (testing "personalized waiting for your decision"
        (is (= :waiting-for-decision
               (:application/todo (model/apply-user-permissions application "handler")))
            "as seen by handler")
        (is (= :waiting-for-your-decision
               (:application/todo (model/apply-user-permissions application "decider1")))
            "as seen by decider"))
      (testing "decider sees all applicant attributes"
        (is (= {:userid "applicant" :email "applicant@example.com" :name "Applicant" :secret "secret"}
               (:application/applicant application)))))))

(deftest test-apply-role-privacy
  (letfn [(answers [application & roles]
            (-> application
                (model/enrich-with-injections injections)
                (model/apply-privacy-by-roles (set (remove nil? roles)))
                (get-in [:application/forms 0 :form/fields])
                (->> (mapv (juxt :field/value
                                 :field/previous-value
                                 :field/privacy
                                 :field/private)))))]

    (doseq [role #{nil :reviewer :past-reviewer :owner :domain-owner}]
      (is (= [["foo" nil nil false] ["bar" nil nil false] ["" nil :private true]]
             (answers submitted-application role))
          (str "role " (pr-str role) " should not see private answers")))

    (doseq [role #{:applicant :member :handler :reporter :decider :past-decider}]
      (is (= [["foo" nil nil false] ["bar" nil nil false] ["private answer" nil :private false]]
             (answers submitted-application role))
          (str "role " (pr-str role) " should not see private answers")))
    (testing "previous value"
      (is (= [["new foo" "foo" nil false]
              ["new bar" "bar" nil false]
              ["" "" :private true]]
             (answers submitted-returned-resubmitted-application :reviewer))
          "should not see previous answers")
      (is (= [["new foo" "foo" nil false]
              ["new bar" "bar" nil false]
              ["new private answer" "private answer" :private false]]
             (answers submitted-returned-resubmitted-application :handler))
          "should see previous answers"))))

(deftest test-hide-attachments
  (let [application {:application/attachments [{:attachment/id 1 :attachment/filename "1.txt"}
                                               {:attachment/id 2 :attachment/filename "2.txt"}
                                               {:attachment/id 3 :attachment/filename "3.txt"}
                                               {:attachment/id 4 :attachment/filename "4.txt"}
                                               {:attachment/id 5 :attachment/filename "5.txt"}
                                               {:attachment/id 6 :attachment/filename "6.txt"}]
                     :application/forms [{:form/fields [{:field/type :attachment
                                                         :field/value "1" :field/previous-value "2"}]}
                                         {:form/fields [{:field/type :text
                                                         :field/value "3,4"}]}]
                     :application/events [{:event/type :application.event/remarked
                                           :application/comment "4"
                                           :event/attachments [{:attachment/id 5}
                                                               {:attachment/id 6}]}]}]
    (is (= [{:attachment/id 1 :attachment/filename "1.txt"}
            {:attachment/id 2 :attachment/filename "2.txt"}
            {:attachment/id 5 :attachment/filename "5.txt"}
            {:attachment/id 6 :attachment/filename "6.txt"}]
           (:application/attachments
            (#'model/hide-attachments application))))))

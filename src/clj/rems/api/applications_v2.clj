(ns rems.api.applications-v2
  (:require [clj-time.core :as time]
            [clojure.set :as set]
            [clojure.test :refer [deftest is testing]]
            [clojure.tools.logging :as log]
            [medley.core :refer [map-vals]]
            [mount.core :refer [defstate]]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.form :as form]
            [rems.db.licenses :as licenses]
            [rems.db.users :as users]
            [rems.workflow.dynamic :as dynamic])
  (:import (org.joda.time DateTime)))

;;; user permissions

(defn update-grants [application grants]
  (reduce (fn [application [subject permissions]]
            (let [category (cond
                             (keyword? subject) :permissions/by-role
                             (string? subject) :permissions/by-user)]
              (if (nil? permissions)
                (update application category dissoc subject)
                (assoc-in application [category subject] (set permissions)))))
          application
          grants))

(deftest test-update-grants
  (testing "role-specific permissions"
    (is (= {:permissions/by-role {:role #{:foo :bar}}}
           (-> {}
               (update-grants {:role [:foo :bar]}))))
    (testing "updating"
      (is (= {:permissions/by-role {:role #{:gazonk}}}
             (-> {}
                 (update-grants {:role [:foo :bar]})
                 (update-grants {:role [:gazonk]})))))
    (testing "removing"
      (is (= {:permissions/by-role {:role #{}}}
             (-> {}
                 (update-grants {:role [:foo :bar]})
                 (update-grants {:role []}))))
      (is (= {:permissions/by-role {}}
             (-> {}
                 (update-grants {:role [:foo :bar]})
                 (update-grants {:role nil}))))))

  (testing "multiple roles"
    (is (= {:permissions/by-role {:role-1 #{:foo}
                                  :role-2 #{:bar}}}
           (-> {}
               (update-grants {:role-1 [:foo]
                               :role-2 [:bar]}))))
    (testing "updating"
      (is (= {:permissions/by-role {:role-1 #{:foo}
                                    :role-2 #{:xyz}}}
             (-> {}
                 (update-grants {:role-1 [:foo]
                                 :role-2 [:bar]})
                 (update-grants {:role-2 [:xyz]})))))
    (testing "removing"
      (is (= {:permissions/by-role {:role-1 #{:foo}}}
             (-> {}
                 (update-grants {:role-1 [:foo]
                                 :role-2 [:bar]})
                 (update-grants {:role-2 nil}))))))

  (testing "user-specific permissions"
    (is (= {:permissions/by-user {"user" #{:foo :bar}}}
           (-> {}
               (update-grants {"user" [:foo :bar]}))))
    (testing "updating"
      (is (= {:permissions/by-user {"user" #{:gazonk}}}
             (-> {}
                 (update-grants {"user" [:foo :bar]})
                 (update-grants {"user" [:gazonk]})))))
    (testing "removing"
      (is (= {:permissions/by-user {}}
             (-> {}
                 (update-grants {"user" [:foo :bar]})
                 (update-grants {"user" nil})))))))

(defn user-permissions [application user-id]
  (let [applicant? (= user-id (:application/applicant application))
        handler? (contains? (:workflow.dynamic/handlers application) user-id)
        permissions (remove nil?
                            [(get-in application [:permissions/by-user user-id])
                             (when applicant?
                               (get-in application [:permissions/by-role :applicant]))
                             (when handler?
                               (get-in application [:permissions/by-role :handler]))])]
    (if (empty? permissions)
      nil
      (apply set/union permissions))))

(deftest test-user-permissions
  (testing "not authorized"
    (is (= nil
           (user-permissions {}
                             "user"))))
  (testing "read-only access"
    (is (= #{}
           (user-permissions {:permissions/by-user {"user" #{}}}
                             "user"))))
  (testing "user permissions"
    (is (= #{:foo}
           (user-permissions {:permissions/by-user {"user" #{:foo}}}
                             "user"))))
  (testing "applicant permissions"
    (is (= #{:foo}
           (user-permissions {:application/applicant "user"
                              :permissions/by-role {:applicant #{:foo}}}
                             "user"))))
  (testing "handler permissions"
    (is (= #{:foo}
           (user-permissions {:workflow.dynamic/handlers #{"user"}
                              :permissions/by-role {:handler #{:foo}}}
                             "user"))))
  (testing "combined permissions from multiple roles"
    (let [application {:application/applicant "user"
                       :workflow.dynamic/handlers #{"user"}
                       :permissions/by-user {"user" #{:foo}}
                       :permissions/by-role {:applicant #{:bar}
                                             :handler #{:gazonk}}}]
      (is (= #{:foo :bar :gazonk}
             (user-permissions application "user")))
      (is (= nil
             (user-permissions application "wrong user"))))))

;;;; v2 API, pure application data

(defmulti ^:private application-view
  (fn [_application event] (:event/type event)))


(defmethod application-view :application.event/created
  [application event]
  (-> application
      (assoc :application/id (:application/id event)
             :application/created (:event/time event)
             :application/modified (:event/time event)
             :application/applicant (:event/actor event)
             :application/resources (map (fn [resource]
                                           {:catalogue-item/id (:catalogue-item/id resource)
                                            :resource/ext-id (:resource/ext-id resource)})
                                         (:application/resources event))
             :application/licenses (map (fn [license]
                                          {:license/id (:license/id license)
                                           :license/accepted false})
                                        (:application/licenses event))
             :application/events []
             :form/id (:form/id event)
             :form/fields []
             :workflow/id (:workflow/id event)
             :workflow/type (:workflow/type event)
             ;; TODO: or would :workflow.dynamic/state be more appropriate?
             :workflow/state :rems.workflow.dynamic/draft ; TODO
             :workflow.dynamic/handlers (:workflow.dynamic/handlers event))
      (update-grants {:applicant #{::dynamic/add-member
                                   ::dynamic/save-draft
                                   ::dynamic/submit}})))

(defn- set-accepted-licences [licenses accepted-licenses]
  (map (fn [license]
         (assoc license :license/accepted (contains? accepted-licenses (:license/id license))))
       licenses))

(defmethod application-view :application.event/draft-saved
  [application event]
  (-> application
      (assoc :application/modified (:event/time event)
             :form/fields (map (fn [[field-id value]]
                                 {:field/id field-id
                                  :field/value value})
                               (:application/field-values event)))
      (update :application/licenses set-accepted-licences (:application/accepted-licenses event))))


(defmethod application-view :application.event/member-added
  [application event]
  application)

(defmethod application-view :application.event/submitted
  [application event]
  (-> application
      (assoc :workflow/state ::dynamic/submitted)
      (update-grants {:applicant #{::dynamic/add-member}
                      :handler #{::dynamic/approve
                                 ::dynamic/reject
                                 ::dynamic/return
                                 ::dynamic/request-decision
                                 ::dynamic/request-comment}})))

(defmethod application-view :application.event/returned
  [application event]
  application)

(defmethod application-view :application.event/comment-requested
  [application event]
  application)

(defmethod application-view :application.event/commented
  [application event]
  application)

(defmethod application-view :application.event/decision-requested
  [application event]
  application)

(defmethod application-view :application.event/decided
  [application event]
  application)

(defmethod application-view :application.event/approved
  [application event]
  application)

(defmethod application-view :application.event/rejected
  [application event]
  application)

(defmethod application-view :application.event/closed
  [application event]
  application)

(deftest test-application-view-handles-all-events
  (is (= (set (keys dynamic/event-schemas))
         (set (keys (methods application-view))))))


(defn- log-event [events event]
  ;; only include events which are about processing the application
  (if (contains? #{:application.event/created
                   :application.event/draft-saved}
                 (:event/type event))
    events
    ;; choose only keys which will be shown in the UI's event log
    (conj events (select-keys event [:event/type
                                     :event/time
                                     :event/actor
                                     :application/comment
                                     :application/commenters
                                     :application/decider
                                     :application/decision]))))

(defn- application-view-common
  [application event]
  (assert (or (nil? (:application/id application))
              (= (:application/id application)
                 (:application/id event)))
          (str "event for wrong application "
               "(not= " (:application/id application) " " (:application/id event) ")"))
  (-> application
      (assoc :application/last-activity (:event/time event))
      (update :application/events log-event event)))

(defn- update-application-view [application event]
  (-> application
      (application-view event)
      (application-view-common event)))

;;; v2 API, external entities (form, resources, licenses etc.)

(defn- merge-lists-by
  "Returns a list of merged elements from list1 and list2
   where f returned the same value for both elements."
  [f list1 list2]
  (let [groups (group-by f (concat list1 list2))
        merged-groups (map-vals #(apply merge %) groups)
        merged-in-order (map (fn [item1]
                               (get merged-groups (f item1)))
                             list1)
        list1-keys (set (map f list1))
        orphans-in-order (filter (fn [item2]
                                   (not (contains? list1-keys (f item2))))
                                 list2)]
    (concat merged-in-order orphans-in-order)))

(deftest test-merge-lists-by
  (testing "merges objects with the same key"
    (is (= [{:id 1 :foo "foo1" :bar "bar1"}
            {:id 2 :foo "foo2" :bar "bar2"}]
           (merge-lists-by :id
                           [{:id 1 :foo "foo1"}
                            {:id 2 :foo "foo2"}]
                           [{:id 1 :bar "bar1"}
                            {:id 2 :bar "bar2"}]))))
  (testing "last list overwrites values"
    (is (= [{:id 1 :foo "B"}]
           (merge-lists-by :id
                           [{:id 1 :foo "A"}]
                           [{:id 1 :foo "B"}]))))
  (testing "first list determines the order"
    (is (= [{:id 1} {:id 2}]
           (merge-lists-by :id
                           [{:id 1} {:id 2}]
                           [{:id 2} {:id 1}])))
    (is (= [{:id 2} {:id 1}]
           (merge-lists-by :id
                           [{:id 2} {:id 1}]
                           [{:id 1} {:id 2}]))))
  ;; TODO: or should the unmatched items be discarded? the primary use case is that some fields are removed from a form (unless forms are immutable)
  (testing "unmatching items are added to the end in order"
    (is (= [{:id 1} {:id 2} {:id 3} {:id 4}]
           (merge-lists-by :id
                           [{:id 1} {:id 2}]
                           [{:id 3} {:id 4}])))
    (is (= [{:id 4} {:id 3} {:id 2} {:id 1}]
           (merge-lists-by :id
                           [{:id 4} {:id 3}]
                           [{:id 2} {:id 1}])))))

(defn- localization-for [key item]
  (into {} (for [lang (keys (:localizations item))]
             (when-let [text (get-in item [:localizations lang key])]
               [lang text]))))

(deftest test-localization-for
  (is (= {:en "en title" :fi "fi title"}
         (localization-for :title {:localizations {:en {:title "en title"}
                                                   :fi {:title "fi title"}}})))
  (is (= {:en "en title"}
         (localization-for :title {:localizations {:en {:title "en title"}
                                                   :fi {}}})))
  (is (= {}
         (localization-for :title {:localizations {:en {}
                                                   :fi {}}}))))

(defn- assoc-form [application form]
  (let [fields (map (fn [item]
                      {:field/id (:id item)
                       :field/value "" ; default for new forms
                       :field/type (keyword (:type item))
                       :field/title (localization-for :title item)
                       :field/placeholder (localization-for :inputprompt item)
                       :field/optional (:optional item)
                       :field/options (:options item)
                       :field/max-length (:maxlength item)})
                    (:items form))
        fields (merge-lists-by :field/id
                               fields
                               (:form/fields application))
        description (->> fields
                         (filter #(= :description (:field/type %)))
                         first
                         :field/value)]
    (assoc application
           :form/title (:title form)
           :form/fields fields
           :application/description description)))

(defn- assoc-resources [application catalogue-items]
  (let [resources (->> catalogue-items
                       (map (fn [item]
                              {:catalogue-item/id (:id item)
                               :resource/id (:resource-id item)
                               :resource/ext-id (:resid item)
                               :catalogue-item/title (assoc (localization-for :title item)
                                                            :default (:title item))
                               :catalogue-item/start (:start item)
                               :catalogue-item/state (keyword (:state item))}))
                       (sort-by :catalogue-item/id))]
    (assoc application :application/resources resources)))

(defn- assoc-licenses [application licenses]
  (let [licenses (->> licenses
                      (map (fn [license]
                             (let [type (keyword (:licensetype license))
                                   content-key (case type
                                                 :link :license/link
                                                 :text :license/text)]
                               {:license/id (:id license)
                                :license/type type
                                :license/start (:start license)
                                :license/end (:end license)
                                :license/title (assoc (localization-for :title license)
                                                      :default (:title license))
                                content-key (assoc (localization-for :textcontent license)
                                                   :default (:textcontent license))})))
                      (sort-by :license/id))]
    (assoc application :application/licenses (merge-lists-by :license/id
                                                             licenses
                                                             (:application/licenses application)))))

(defn- assoc-externals [application {:keys [get-form get-catalogue-item get-license get-user]}]
  (-> application
      (assoc-form (get-form (:form/id application)))
      (assoc-resources (->> (:application/resources application)
                            (map :catalogue-item/id)
                            (map get-catalogue-item)))
      (assoc-licenses (->> (:application/licenses application)
                           (map :license/id)
                           (map get-license)))
      (assoc :application/applicant-attributes (get-user (:application/applicant application)))))

(defn- build-application-view [events externals]
  (-> (reduce update-application-view nil events)
      (assoc-externals externals)))

(defn- valid-events [events]
  (doseq [event events]
    (applications/validate-dynamic-event event))
  events)

(deftest test-application-view
  (let [externals {:get-form {40 {:id 40
                                  :organization "org"
                                  :title "form title"
                                  :start (DateTime. 100)
                                  :end nil
                                  :items [{:id 41
                                           :localizations {:en {:title "en title"
                                                                :inputprompt "en placeholder"}
                                                           :fi {:title "fi title"
                                                                :inputprompt "fi placeholder"}}
                                           :optional false
                                           :options []
                                           :maxlength 100
                                           :type "description"}
                                          {:id 42
                                           :localizations {:en {:title "en title"
                                                                :inputprompt "en placeholder"}
                                                           :fi {:title "fi title"
                                                                :inputprompt "fi placeholder"}}
                                           :optional false
                                           :options []
                                           :maxlength 100
                                           :type "text"}]}}

                   :get-catalogue-item {10 {:id 10
                                            :resource-id 11
                                            :resid "urn:11"
                                            :wfid 50
                                            :formid 40
                                            :title "non-localized title"
                                            :localizations {:en {:id 10
                                                                 :langcode :en
                                                                 :title "en title"}
                                                            :fi {:id 10
                                                                 :langcode :fi
                                                                 :title "fi title"}}
                                            :start (DateTime. 100)
                                            :state "enabled"}
                                        20 {:id 20
                                            :resource-id 21
                                            :resid "urn:21"
                                            :wfid 50
                                            :formid 40
                                            :title "non-localized title"
                                            :localizations {:en {:id 20
                                                                 :langcode :en
                                                                 :title "en title"}
                                                            :fi {:id 20
                                                                 :langcode :fi
                                                                 :title "fi title"}}
                                            :start (DateTime. 100)
                                            :state "enabled"}}

                   :get-license {30 {:id 30
                                     :licensetype "link"
                                     :start (DateTime. 100)
                                     :end nil
                                     :title "non-localized title"
                                     :textcontent "http://non-localized-license-link"
                                     :localizations {:en {:title "en title"
                                                          :textcontent "http://en-license-link"}
                                                     :fi {:title "fi title"
                                                          :textcontent "http://fi-license-link"}}}
                                 31 {:id 31
                                     :licensetype "text"
                                     :start (DateTime. 100)
                                     :end nil
                                     :title "non-localized title"
                                     :textcontent "non-localized license text"
                                     :localizations {:en {:title "en title"
                                                          :textcontent "en license text"}
                                                     :fi {:title "fi title"
                                                          :textcontent "fi license text"}}}}

                   :get-user {"applicant" {"eppn" "applicant"
                                           "mail" "applicant@example.com"
                                           "commonName" "Applicant"}}}

        ;; expected values
        new-application {:application/id 1
                         :application/created (DateTime. 1000)
                         :application/modified (DateTime. 1000)
                         :application/last-activity (DateTime. 1000)
                         :application/applicant "applicant"
                         :application/applicant-attributes {"eppn" "applicant"
                                                            "mail" "applicant@example.com"
                                                            "commonName" "Applicant"}
                         :application/resources [{:catalogue-item/id 10
                                                  :resource/id 11
                                                  :resource/ext-id "urn:11"
                                                  :catalogue-item/title {:en "en title"
                                                                         :fi "fi title"
                                                                         :default "non-localized title"}
                                                  :catalogue-item/start (DateTime. 100)
                                                  :catalogue-item/state :enabled}
                                                 {:catalogue-item/id 20
                                                  :resource/id 21
                                                  :resource/ext-id "urn:21"
                                                  :catalogue-item/title {:en "en title"
                                                                         :fi "fi title"
                                                                         :default "non-localized title"}
                                                  :catalogue-item/start (DateTime. 100)
                                                  :catalogue-item/state :enabled}]
                         :application/licenses [{:license/id 30
                                                 :license/accepted false
                                                 :license/type :link
                                                 :license/start (DateTime. 100)
                                                 :license/end nil
                                                 :license/title {:en "en title"
                                                                 :fi "fi title"
                                                                 :default "non-localized title"}
                                                 :license/link {:en "http://en-license-link"
                                                                :fi "http://fi-license-link"
                                                                :default "http://non-localized-license-link"}}
                                                {:license/id 31
                                                 :license/accepted false
                                                 :license/type :text
                                                 :license/start (DateTime. 100)
                                                 :license/end nil
                                                 :license/title {:en "en title"
                                                                 :fi "fi title"
                                                                 :default "non-localized title"}
                                                 :license/text {:en "en license text"
                                                                :fi "fi license text"
                                                                :default "non-localized license text"}}]
                         :application/events []
                         :application/description ""
                         :form/id 40
                         :form/title "form title"
                         :form/fields [{:field/id 41
                                        :field/value ""
                                        :field/type :description
                                        :field/title {:en "en title" :fi "fi title"}
                                        :field/placeholder {:en "en placeholder" :fi "fi placeholder"}
                                        :field/optional false
                                        :field/options []
                                        :field/max-length 100}
                                       {:field/id 42
                                        :field/value ""
                                        :field/type :text
                                        :field/title {:en "en title" :fi "fi title"}
                                        :field/placeholder {:en "en placeholder" :fi "fi placeholder"}
                                        :field/optional false
                                        :field/options []
                                        :field/max-length 100}]
                         :workflow/id 50
                         :workflow/type :dynamic
                         :workflow/state :rems.workflow.dynamic/draft
                         :workflow.dynamic/handlers #{"handler"}
                         :permissions/by-role {:applicant #{::dynamic/add-member
                                                            ::dynamic/save-draft
                                                            ::dynamic/submit}}}


        ;; test double events
        created-event {:event/type :application.event/created
                       :event/time (DateTime. 1000)
                       :event/actor "applicant"
                       :application/id 1
                       :application/resources [{:catalogue-item/id 10
                                                :resource/ext-id "urn:11"}
                                               {:catalogue-item/id 20
                                                :resource/ext-id "urn:21"}]
                       :application/licenses [{:license/id 30}
                                              {:license/id 31}]
                       :form/id 40
                       :workflow/id 50
                       :workflow/type :dynamic
                       :workflow.dynamic/handlers #{"handler"}}]

    (testing "new application"
      (is (= new-application
             (build-application-view
              (valid-events
               [created-event])
              externals))))

    (testing "draft saved"
      (is (= (-> new-application
                 (assoc-in [:application/modified] (DateTime. 2000))
                 (assoc-in [:application/last-activity] (DateTime. 2000))
                 (assoc-in [:application/licenses 0 :license/accepted] true)
                 (assoc-in [:application/licenses 1 :license/accepted] true)
                 (assoc-in [:application/description] "foo")
                 (assoc-in [:form/fields 0 :field/value] "foo")
                 (assoc-in [:form/fields 1 :field/value] "bar"))
             (build-application-view
              (valid-events
               [created-event
                {:event/type :application.event/draft-saved
                 :event/time (DateTime. 2000)
                 :event/actor "applicant"
                 :application/id 1
                 :application/field-values {41 "foo"
                                            42 "bar"}
                 :application/accepted-licenses #{30 31}}])
              externals))))

    (testing "submitted"
      (is (= (-> new-application
                 (assoc-in [:application/last-activity] (DateTime. 2000))
                 (assoc-in [:application/events] [{:event/type :application.event/submitted
                                                   :event/time (DateTime. 2000)
                                                   :event/actor "applicant"}])
                 (assoc-in [:workflow/state] ::dynamic/submitted)
                 (assoc-in [:permissions/by-role :applicant] #{::dynamic/add-member})
                 (assoc-in [:permissions/by-role :handler] #{::dynamic/approve
                                                             ::dynamic/reject
                                                             ::dynamic/return
                                                             ::dynamic/request-decision
                                                             ::dynamic/request-comment}))
             (build-application-view
              (valid-events
               [created-event
                {:event/type :application.event/submitted
                 :event/time (DateTime. 2000)
                 :event/actor "applicant"
                 :application/id 1}])
              externals))))))

(defn- get-form [form-id]
  (-> (form/get-form form-id)
      (select-keys [:id :organization :title :start :end])
      (assoc :items (->> (db/get-form-items {:id form-id})
                         (mapv #(applications/process-item nil form-id %))))))

(defn- get-catalogue-item [catalogue-item-id]
  (assert (int? catalogue-item-id)
          (pr-str catalogue-item-id))
  (first (applications/get-catalogue-items [catalogue-item-id])))

(defn- get-license [license-id]
  (licenses/get-license license-id))

(defn- get-user [user-id]
  (users/get-user-attributes user-id))

(def externals {:get-form get-form
                :get-catalogue-item get-catalogue-item
                :get-license get-license
                :get-user get-user})

(defn apply-user-permissions [application user-id]
  (when-let [permissions (user-permissions application user-id)]
    ;; TODO: hide sensitive information from applicant (most event comments, maybe some events also)
    ;;       - could add :see-everything permission to the appropriate roles and check for it here
    ;;       https://github.com/CSCfi/rems/issues/859
    (-> application
        (assoc :permissions/current-user permissions)
        (dissoc :permissions/by-user
                :permissions/by-role))))

(defn api-get-application-v2 [user-id application-id]
  (let [events (applications/get-dynamic-application-events application-id)]
    (when (not (empty? events))
      (-> (build-application-view events externals)
          (apply-user-permissions user-id)))))

;;; v1 API compatibility layer

(defn- assoc-derived-data [user-id application]
  (assoc application
         :can-approve? (applications/can-approve? user-id application)
         :can-close? (applications/can-close? user-id application)
         :can-withdraw? (applications/can-withdraw? user-id application)
         :can-third-party-review? (applications/can-third-party-review? user-id application)
         :is-applicant? (applications/is-applicant? user-id application)))

(defn- transform-v2-to-v1 [application user-id]
  (let [catalogue-items (map (fn [resource]
                               (applications/translate-catalogue-item
                                {:id (:catalogue-item/id resource)
                                 :resource-id (:resource/id resource)
                                 :resid (:resource/ext-id resource)
                                 :wfid (:workflow/id application)
                                 :formid (:form/id application)
                                 :start (:catalogue-item/start resource)
                                 :state (name (:catalogue-item/state resource))
                                 :title (:default (:catalogue-item/title resource))
                                 :localizations (into {} (for [lang (-> (set (keys (:catalogue-item/title resource)))
                                                                        (disj :default))]
                                                           [lang {:title (get-in resource [:catalogue-item/title lang])
                                                                  :langcode lang
                                                                  :id (:catalogue-item/id resource)}]))}))
                             (:application/resources application))]
    {:id (:form/id application)
     :title (:form/title application)
     :catalogue-items catalogue-items
     :applicant-attributes (:application/applicant-attributes application)
     :application (assoc-derived-data
                   user-id
                   {:id (:application/id application)
                    :formid (:form/id application)
                    :wfid (:workflow/id application)
                    :applicantuserid (:application/applicant application)
                    :start (:application/created application)
                    :last-modified (:application/last-activity application)
                    :state (:workflow/state application) ; TODO: round-based workflows
                    :description (:application/description application)
                    :catalogue-items catalogue-items
                    :form-contents {:items (into {} (for [field (:form/fields application)]
                                                      [(:field/id field) (:field/value field)]))
                                    :licenses (into {} (for [license (:application/licenses application)]
                                                         (when (:license/accepted license)
                                                           [(:license/id license) "approved"])))}
                    :events [] ; TODO: round-based workflows
                    :dynamic-events (:application/events application)
                    :workflow {:type (:workflow/type application)
                               ;; TODO: add :handlers only when it exists? https://stackoverflow.com/a/16375390
                               :handlers (vec (:workflow.dynamic/handlers application))}
                    :possible-commands (:permissions/current-user application)
                    :fnlround 0 ; TODO: round-based workflows
                    :review-type nil}) ; TODO: round-based workflows
     :phases (applications/get-application-phases (:workflow/state application))
     :licenses (map (fn [license]
                      {:id (:license/id license)
                       :type "license"
                       :licensetype (name (:license/type license))
                       ;; TODO: Licenses have three different start times: license.start, workflow_licenses.start, resource_licenses.start
                       ;;       (also catalogue_item_application_licenses.start but that table looks unused)
                       ;;       The old API returns either workflow_licenses.start or resource_licenses.start,
                       ;;       the new one returns license.start for now. Should we keep all three or simplify?
                       :start (:license/start license)
                       :end (:license/end license)
                       :approved (:license/accepted license)
                       :title (:default (:license/title license))
                       :textcontent (:default (or (:license/link license)
                                                  (:license/text license)))
                       :localizations (into {} (for [lang (-> (set (concat (keys (:license/title license))
                                                                           (keys (:license/link license))
                                                                           (keys (:license/text license))))
                                                              (disj :default))]
                                                 [lang {:title (get-in license [:license/title lang])
                                                        :textcontent (or (get-in license [:license/link lang])
                                                                         (get-in license [:license/text lang]))}]))})
                    (:application/licenses application))
     :items (map (fn [field]
                   {:id (:field/id field)
                    :type (name (:field/type field))
                    :optional (:field/optional field)
                    :options (:field/options field)
                    :maxlength (:field/max-length field)
                    :value (:field/value field)
                    :previous-value nil ; TODO
                    :localizations (into {} (for [lang (set (concat (keys (:field/title field))
                                                                    (keys (:field/placeholder field))))]
                                              [lang {:title (get-in field [:field/title lang])
                                                     :inputprompt (get-in field [:field/placeholder lang])}]))})
                 (:form/fields application))}))

(defn api-get-application-v1 [user-id application-id]
  (when-let [v2 (api-get-application-v2 user-id application-id)]
    (transform-v2-to-v1 v2 user-id)))

;;; v2 API, listing all applications

(defn- apply-event [applications event]
  (if-let [app-id (:application/id event)] ; old style events don't have :application/id
    (update applications app-id update-application-view event)
    applications))

(defn- apply-events [applications events]
  (reduce apply-event applications events))

(defn- reset-state [_]
  {:last-processed-event-id 0
   :applications {}})

(def projection-state (agent (reset-state nil)
                             :error-handler (fn [_agent exception]
                                              (log/error exception "Updating projection failed"))))
(comment
  @projection-state
  (send projection-state reset-state))

(defn update-projection [state]
  (let [from-id (:last-processed-event-id state)
        batch-size 1000
        events (applications/get-dynamic-application-events-since from-id batch-size)
        until-id (:event/id (last events))]
    (if (empty? events)
      state
      (do
        (log/info "Updating projection from" from-id "until" until-id)
        (when (= batch-size (count events))
          ;; there may be more events, so handle the next batch immediately after the current one
          (send-off projection-state update-projection))
        (assoc state
               :last-processed-event-id until-id
               :applications (apply-events (:applications state) events))))))

(defstate projection-updater
  :start (future (while (not (Thread/interrupted))
                   (send-off projection-state update-projection)
                   (Thread/sleep (-> 60 time/seconds time/in-millis))))
  :stop (future-cancel projection-updater))

(defn get-user-applications-v2 [user-id]
  (->> (vals (:applications @projection-state))
       (map #(apply-user-permissions % user-id))
       (remove nil?)
       ;; TODO: do this eagerly for caching? would need to make assoc-externals idempotent
       (map #(assoc-externals % externals))
       ;; remove unnecessary data from summmary
       (map #(dissoc % :form/fields :application/licenses))))

(ns rems.api.applications-v2
  (:require [clojure.test :refer [deftest is testing]]
            [medley.core :refer [map-vals]]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.workflow.dynamic :as dynamic])
  (:import (org.joda.time DateTime)))

(defmulti ^:private application-view
  (fn [_application event] (:event event)))


(defmethod application-view :event/created
  [application event]
  (assoc application
         :application-id (:application-id event)
         :created (:time event)
         :applicant (:actor event)
         :resources (:resources event)
         :licenses (map (fn [license]
                          (assoc license :accepted false))
                        (:licenses event))
         :form-id (:form-id event)
         :form-fields []
         :workflow-id (:workflow-id event)
         :workflow-type (:workflow-type event)))


(defn- set-accepted-licences [licenses acceptance]
  (map (fn [license]
         (assoc license :accepted (= "accepted" (get acceptance (:license-id license)))))
       licenses))

(defmethod application-view :event/draft-saved
  [application event]
  (-> application
      (assoc :form-fields (map (fn [[field-id value]]
                                 {:field-id field-id
                                  :value value})
                               (:items event)))
      (update :licenses set-accepted-licences (:licenses event))))


(defmethod application-view :event/member-added
  [application event]
  application)

(defmethod application-view :event/submitted
  [application event]
  application)

(defmethod application-view :event/returned
  [application event]
  application)

(defmethod application-view :event/comment-requested
  [application event]
  application)

(defmethod application-view :event/commented
  [application event]
  application)

(defmethod application-view :event/decision-requested
  [application event]
  application)

(defmethod application-view :event/decided
  [application event]
  application)

(defmethod application-view :event/approved
  [application event]
  application)

(defmethod application-view :event/rejected
  [application event]
  application)

(defmethod application-view :event/closed
  [application event]
  application)

(deftest test-application-view-handles-all-events
  (is (= (set (keys dynamic/event-schemas))
         (set (keys (methods application-view))))))


(defn- application-view-common
  [application event]
  (assoc application
         :modified (:time event)))

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
  ;; TODO: or should the unmatched items be discarded? that would happen if some fields are removed from a form
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
  (let [form-fields (map (fn [item]
                           {:field-id (:id item)
                            :value "" ; default for new forms
                            :type (keyword (:type item))
                            :title (localization-for :title item)
                            :placeholder (localization-for :inputprompt item)
                            :optional (:optional item)
                            :options (:options item)
                            :max-length (:maxlength item)})
                         (:items form))]
    (assoc application :form-fields (merge-lists-by :field-id form-fields (:form-fields application)))))

(defn- build-application-view [events {:keys [forms]}]
  (let [application (reduce (fn [application event]
                              (-> application
                                  (application-view event)
                                  (application-view-common event)))
                            {}
                            events)]
    (assoc-form application (forms (:form-id application)))))

(defn- valid-events [events]
  (doseq [event events]
    (applications/validate-dynamic-event event))
  events)

(deftest test-application-view
  (let [externals {:forms {40 {:items [{:id 41
                                        :localizations {:en {:title "en title"
                                                             :inputprompt "en placeholder"}
                                                        :fi {:title "fi title"
                                                             :inputprompt "fi placeholder"}}
                                        :optional false
                                        :options []
                                        :maxlength 100
                                        :type "text"}
                                       {:id 42
                                        :localizations {:en {:title "en title"
                                                             :inputprompt "en placeholder"}
                                                        :fi {:title "fi title"
                                                             :inputprompt "fi placeholder"}}
                                        :optional false
                                        :options []
                                        :maxlength 100
                                        :type "text"}]}}}

        ;; expected values
        new-application {:application-id 1
                         :created (DateTime. 1000)
                         :modified (DateTime. 1000)
                         :applicant "applicant"
                         ;; TODO: resource details
                         :resources [{:catalogue-item-id 10
                                      :resource-ext-id "urn:11"}
                                     {:catalogue-item-id 20
                                      :resource-ext-id "urn:21"}]
                         ;; TODO: license details
                         :licenses [{:license-id 30
                                     :accepted false}
                                    {:license-id 31
                                     :accepted false}]
                         :form-id 40
                         :form-fields [{:field-id 41
                                        :value ""
                                        :type :text
                                        :title {:en "en title" :fi "fi title"}
                                        :placeholder {:en "en placeholder" :fi "fi placeholder"}
                                        :optional false
                                        :options []
                                        :max-length 100}
                                       {:field-id 42
                                        :value ""
                                        :type :text
                                        :title {:en "en title" :fi "fi title"}
                                        :placeholder {:en "en placeholder" :fi "fi placeholder"}
                                        :optional false
                                        :options []
                                        :max-length 100}]
                         ;; TODO: workflow details (e.g. allowed commands)
                         :workflow-id 50
                         :workflow-type :dynamic}

        ;; test double events
        created-event {:event :event/created
                       :application-id 1
                       :time (DateTime. 1000)
                       :actor "applicant"
                       :resources [{:catalogue-item-id 10
                                    :resource-ext-id "urn:11"}
                                   {:catalogue-item-id 20
                                    :resource-ext-id "urn:21"}]
                       :licenses [{:license-id 30}
                                  {:license-id 31}]
                       :form-id 40
                       :workflow-id 50
                       :workflow-type :dynamic
                       :workflow-handlers ["handler"]}]

    (testing "new application"
      (is (= new-application
             (build-application-view
              (valid-events
               [created-event])
              externals))))

    (testing "draft saved"
      (is (= (-> new-application
                 (assoc-in [:modified] (DateTime. 2000))
                 (assoc-in [:licenses 0 :accepted] true)
                 (assoc-in [:licenses 1 :accepted] true)
                 (assoc-in [:form-fields 0 :value] "foo")
                 (assoc-in [:form-fields 1 :value] "bar"))
             (build-application-view
              (valid-events
               [created-event
                {:event :event/draft-saved
                 :application-id 42
                 :time (DateTime. 2000)
                 :actor "applicant"
                 ;; TODO: rename to :field-values
                 :items {41 "foo"
                         42 "bar"}
                 ;; TODO: change to `:accepted-licenses [30 31]` or separate to a license-accepted event
                 :licenses {30 "accepted"
                            31 "accepted"}}])
              externals))))))

(defn- get-form [form-id]
  {:items (->> (db/get-form-items {:id form-id})
               (mapv #(applications/process-item nil form-id %)))})

(defn api-get-application-v2 [user-id application-id]
  (let [events (applications/get-dynamic-application-events application-id)]
    (when (not (empty? events))
      ;; TODO: return just the view
      {:id application-id
       :view (build-application-view events {:forms get-form})
       :events events})))

(defn- transform-v2-to-v1 [application]
  (let [catalogue-items (map (fn [resource]
                               {:id (:catalogue-item-id resource)
                                :resid (:resource-ext-id resource)
                                :wfid (:workflow-id application)
                                :formid (:form-id application)
                                :start nil ; TODO
                                :state nil ; TODO
                                :title nil ; TODO
                                :langcode nil ; TODO
                                :localizations nil}) ; TODO
                             (:resources application))]
    {:id (:form-id application)
     :catalogue-items catalogue-items
     :applicant-attributes {:eppn (:applicant application)
                            :mail nil ; TODO
                            :commonName nil} ; TODO
     :application {:id (:application-id application)
                   :formid (:form-id application)
                   :wfid (:workflow-id application)
                   :applicantuserid (:applicant application)
                   :start (:created application)
                   :last-modified (:modified application)
                   :state nil ; TODO
                   :description nil ; TODO
                   :catalogue-items catalogue-items
                   :form-contents {:items {} ; TODO
                                   :licenses {}} ; TODO
                   :events [] ; TODO
                   :dynamic-events [] ; TODO
                   :workflow {:type (:workflow-type application)
                              :handlers []} ; TODO
                   :possible-commands [] ; TODO
                   :fnlround nil ; TODO
                   :review-type nil ; TODO
                   :is-applicant? nil ; TODO
                   :can-approve? nil ; TODO
                   :can-third-party-review? nil ; TODO
                   :can-withdraw? nil ; TODO
                   :can-close? nil} ; TODO
     :licenses (map (fn [license]
                      {:id (:license-id license)
                       :type "license"
                       :licensetype nil ; TODO
                       :title nil ; TODO
                       :start nil ; TODO
                       :end nil ; TODO
                       :approved (:accepted license)
                       :textcontent nil ; TODO
                       :localizations nil}) ; TODO
                    (:licenses application))
     :phases [] ; TODO
     :title "" ; TODO
     :items (map (fn [field]
                   {:id (:field-id field)
                    :type (:type field)
                    :optional (:optional field)
                    :options (:options field)
                    :maxlength (:maxlength field)
                    :value (:value field)
                    :previous-value nil ; TODO
                    :localizations (into {} (for [lang (distinct (concat (keys (:title field))
                                                                         (keys (:placeholder field))))]
                                              [lang {:title (get-in field [:title lang])
                                                     :inputprompt (get-in field [:placeholder lang])}]))})
                 (:form-fields application))}))

(defn api-get-application-v1 [user-id application-id]
  (let [v2 (api-get-application-v2 user-id application-id)]
    (transform-v2-to-v1 (:view v2))))

(ns ^:focused rems.api.applications-v2
  (:require [clojure.test :refer [deftest is testing]]
            [rems.db.applications :as applications]
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
         ;; TODO: hard-coded form
         :form-fields [{:field-id 5
                        :value ""}
                       {:field-id 6
                        :value ""}]
         ;; TODO: hard-coded licenses
         :licenses [{:license-id 7
                     :accepted false}
                    {:license-id 8
                     :accepted false}]))


(defn- set-form-field-values [fields new-values]
  (map (fn [field]
         (assoc field :value (get new-values (:field-id field))))
       fields))

(defn- set-accepted-licences [licenses acceptance]
  (map (fn [license]
         (assoc license :accepted (= "accepted" (get acceptance (:license-id license)))))
       licenses))

(defmethod application-view :event/draft-saved
  [application event]
  (-> application
      (update :form-fields set-form-field-values (:items event))
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
  (testing "unmatching items are added to the end in order"
    (is (= [{:id 1} {:id 2} {:id 3} {:id 4}]
           (merge-lists-by :id
                           [{:id 1} {:id 2}]
                           [{:id 3} {:id 4}])))
    (is (= [{:id 4} {:id 3} {:id 2} {:id 1}]
           (merge-lists-by :id
                           [{:id 4} {:id 3}]
                           [{:id 2} {:id 1}])))))

(defn- build-application-view [events]
  (reduce (fn [application event]
            (-> application
                (application-view event)
                (application-view-common event)))
          {}
          events))

(defn- valid-events [events]
  (doseq [event events]
    (applications/validate-dynamic-event event))
  events)

(deftest test-application-view
  (testing "new application"
    ;; TODO: catalogue items
    (is (= {:application-id 42
            :created (DateTime. 1000)
            :modified (DateTime. 1000)
            :applicant "applicant"
            :form-fields [{:field-id 5
                           :value ""}
                          {:field-id 6
                           :value ""}]
            :licenses [{:license-id 7
                        :accepted false}
                       {:license-id 8
                        :accepted false}]}
           (build-application-view
            (valid-events
             [{:event :event/created
               :application-id 42
               :time (DateTime. 1000)
               :actor "applicant"
               :catalogue-items [3 4]}])))))
  (testing "saved draft"
    (is (= {:application-id 42
            :created (DateTime. 1000)
            :modified (DateTime. 2000)
            :applicant "applicant"
            :form-fields [{:field-id 5
                           :value "foo"}
                          {:field-id 6
                           :value "bar"}]
            :licenses [{:license-id 7
                        :accepted true}
                       {:license-id 8
                        :accepted true}]}
           (build-application-view
            (valid-events
             [{:event :event/created
               :application-id 42
               :time (DateTime. 1000)
               :actor "applicant"
               :catalogue-items [3 4]}
              {:event :event/draft-saved
               :application-id 42
               :time (DateTime. 2000)
               :actor "applicant"
               ;; TODO: rename to :fields
               :items {5 "foo"
                       6 "bar"}
               :licenses {7 "accepted"
                          8 "accepted"}}]))))))

(defn api-get-application-v2 [user-id application-id]
  (let [events (applications/get-dynamic-application-events application-id)]
    (when (not (empty? events))
      ;; TODO: return just the view
      {:id application-id
       :view (build-application-view events)
       :events events})))

(defn- transform-v2-to-v1 [application]
  {:id nil ; TODO
   :catalogue-items [] ; TODO
   :applicant-attributes {} ; TODO
   :application {} ; TODO
   :licenses [] ; TODO
   :phases [] ; TODO
   :title "" ; TODO
   :items []}) ; TODO

(defn api-get-application-v1 [user-id application-id]
  (let [v2 (api-get-application-v2 user-id application-id)]
    (transform-v2-to-v1 (:view v2))))

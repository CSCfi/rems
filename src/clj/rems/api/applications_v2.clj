(ns ^:focused rems.api.applications-v2
  (:require [clojure.test :refer [deftest is testing]]
            [rems.db.applications :as applications]))

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


(defn- application-view-common
  [application event]
  (assoc application
         :modified (:time event)))

(defn- build-application-view [events]
  (reduce (fn [application event]
            (-> application
                (application-view event)
                (application-view-common event)))
          {}
          events))

(deftest test-application-view
  (testing "new application"
    ;; TODO: catalogue items
    (is (= {:application-id 42
            :created 1000
            :modified 1000
            :applicant "applicant"
            :form-fields [{:field-id 5
                           :value ""}
                          {:field-id 6
                           :value ""}]
            :licenses [{:license-id 7
                        :accepted false}
                       {:license-id 8
                        :accepted false}]}
           (build-application-view [{:event :event/created
                                     :application-id 42
                                     :time 1000
                                     :actor "applicant"
                                     :catalogue-items [3 4]}]))))
  (testing "saved draft"
    (is (= {:application-id 42
            :created 1000
            :modified 2000
            :applicant "applicant"
            :form-fields [{:field-id 5
                           :value "foo"}
                          {:field-id 6
                           :value "bar"}]
            :licenses [{:license-id 7
                        :accepted true}
                       {:license-id 8
                        :accepted true}]}
           (build-application-view [{:event :event/created
                                     :application-id 42
                                     :time 1000
                                     :actor "applicant"}
                                    {:event :event/draft-saved
                                     :application-id 42
                                     :time 2000
                                     :actor "applicant"
                                     ;; TODO: rename to :fields
                                     :items {5 "foo"
                                             6 "bar"}
                                     :licenses {7 "accepted"
                                                8 "accepted"}}])))))

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

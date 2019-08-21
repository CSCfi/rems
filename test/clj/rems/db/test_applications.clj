(ns ^:integration rems.db.test-applications
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [clojure.test.check.generators :as generators]
            [rems.application.events :as events]
            [rems.db.applications :as applications :refer [application-created-event! application-external-id!]]
            [rems.db.core :as db]
            [rems.db.events :as db-events]
            [rems.db.test-data :as test-data]
            [rems.db.testing :refer [test-db-fixture rollback-db-fixture test-data-fixture]]
            [rems.util :refer [try-catch-ex]]
            [schema-generators.generators :as sg])
  (:import (org.joda.time DateTime DateTimeZone)
           (clojure.lang ExceptionInfo)))

(use-fixtures
  :once
  test-db-fixture
  rollback-db-fixture
  test-data-fixture)

(deftest test-event-serialization
  (testing "round trip serialization"
    (let [generators {DateTime (generators/fmap #(DateTime. ^long % DateTimeZone/UTC)
                                                (generators/large-integer* {:min 0}))}]
      (doseq [event (sg/sample 100 events/Event generators)]
        (is (= event (-> event db-events/event->json db-events/json->event))))))

  (testing "event->json validates events"
    (is (not
         (:rems.event/validate-event
          (try-catch-ex (db-events/event->json {}))))))

  (testing "json->event validates events"
    (is (thrown-with-msg? ExceptionInfo #"Value does not match schema" (db-events/json->event "{}"))))

  (testing "json data format"
    (let [event {:event/type :application.event/submitted
                 :event/time (DateTime. 2020 1 1 12 0 0 (DateTimeZone/forID "Europe/Helsinki"))
                 :event/actor "foo"
                 :application/id 123}
          json (db-events/event->json event)]
      (is (str/includes? json "\"event/time\":\"2020-01-01T10:00:00.000Z\"")))))

(deftest test-application-created-event!
  (let [wf-id (test-data/create-dynamic-workflow! {})
        form-id (test-data/create-form! {})
        res-id (test-data/create-resource! {:resource-ext-id "res1"})
        cat-id (test-data/create-catalogue-item! {:resource-id res-id
                                                  :form-id form-id
                                                  :workflow-id wf-id})
        next-expected-app-id (let [id (atom (:id (first (jdbc/query db/*db* ["SELECT nextval('catalogue_item_application_id_seq') AS id"]))))]
                               (fn []
                                 (swap! id inc)))]

    (testing "minimal application"
      (is (= {:event/type :application.event/created
              :event/actor "alice"
              :event/time (DateTime. 1000)
              :application/id (next-expected-app-id)
              :application/external-id "1970/1"
              :application/resources [{:catalogue-item/id cat-id
                                       :resource/ext-id "res1"}]
              :application/licenses []
              :form/id form-id
              :workflow/id wf-id
              :workflow/type :workflow/dynamic}
             (application-created-event! {:catalogue-item-ids [cat-id]
                                          :time (DateTime. 1000)
                                          :actor "alice"}
                                         applications/db-injections))))

    (testing "multiple resources"
      (let [res-id2 (test-data/create-resource! {:resource-ext-id "res2"})
            cat-id2 (test-data/create-catalogue-item! {:resource-id res-id2
                                                       :form-id form-id
                                                       :workflow-id wf-id})]
        (is (= {:event/type :application.event/created
                :event/actor "alice"
                :event/time (DateTime. 1000)
                :application/id (next-expected-app-id)
                :application/external-id "1970/2"
                :application/resources [{:catalogue-item/id cat-id
                                         :resource/ext-id "res1"}
                                        {:catalogue-item/id cat-id2
                                         :resource/ext-id "res2"}]
                :application/licenses []
                :form/id form-id
                :workflow/id wf-id
                :workflow/type :workflow/dynamic}
               (application-created-event! {:catalogue-item-ids [cat-id cat-id2]
                                            :time (DateTime. 1000)
                                            :actor "alice"}
                                           applications/db-injections)))))

    (testing "error: zero catalogue items"
      (is (thrown-with-msg? AssertionError #"catalogue item not specified"
                            (application-created-event! {:catalogue-item-ids []
                                                         :time (DateTime. 1000)
                                                         :actor "alice"}
                                                        applications/db-injections))))

    (testing "error: non-existing catalogue items"
      (is (thrown-with-msg? AssertionError #"catalogue item 999999 not found"
                            (application-created-event! {:catalogue-item-ids [999999]
                                                         :time (DateTime. 1000)
                                                         :actor "alice"}
                                                        applications/db-injections))))

    (testing "error: catalogue items with different forms"
      (let [form-id2 (test-data/create-form! {})
            res-id2 (test-data/create-resource! {:resource-ext-id "res2+"})
            cat-id2 (test-data/create-catalogue-item! {:resource-id res-id2
                                                       :form-id form-id2
                                                       :workflow-id wf-id})]
        (is (thrown-with-msg? AssertionError #"catalogue items did not have the same form"
                              (application-created-event! {:catalogue-item-ids [cat-id cat-id2]
                                                           :time (DateTime. 1000)
                                                           :actor "alice"}
                                                          applications/db-injections)))))

    (testing "error: catalogue items with different workflows"
      (let [wf-id2 (test-data/create-dynamic-workflow! {})
            res-id2 (test-data/create-resource! {:resource-ext-id "res2++"})
            cat-id2 (test-data/create-catalogue-item! {:resource-id res-id2
                                                       :form-id form-id
                                                       :workflow-id wf-id2})]
        (is (thrown-with-msg? AssertionError #"catalogue items did not have the same workflow"
                              (application-created-event! {:catalogue-item-ids [cat-id cat-id2]
                                                           :time (DateTime. 1000)
                                                           :actor "alice"}
                                                          applications/db-injections)))))

    (testing "resource licenses"
      (let [lic-id (test-data/create-license! {})
            res-id2 (test-data/create-resource! {:resource-ext-id "res2+++"
                                                 :license-ids [lic-id]})
            cat-id2 (test-data/create-catalogue-item! {:resource-id res-id2
                                                       :form-id form-id
                                                       :workflow-id wf-id})]
        (is (= {:event/type :application.event/created
                :event/actor "alice"
                :event/time (DateTime. 1000)
                :application/id (next-expected-app-id)
                :application/external-id "1970/3"
                :application/resources [{:catalogue-item/id cat-id2
                                         :resource/ext-id "res2+++"}]
                :application/licenses [{:license/id lic-id}]
                :form/id form-id
                :workflow/id wf-id
                :workflow/type :workflow/dynamic}
               (application-created-event! {:catalogue-item-ids [cat-id2]
                                            :time (DateTime. 1000)
                                            :actor "alice"}
                                           applications/db-injections)))))

    (testing "workflow licenses"
      (let [lic-id (test-data/create-license! {})
            wf-id2 (test-data/create-dynamic-workflow! {})
            _ (db/create-workflow-license! {:wfid wf-id2 :licid lic-id})
            cat-id2 (test-data/create-catalogue-item! {:resource-id res-id
                                                       :form-id form-id
                                                       :workflow-id wf-id2})]
        (is (= {:event/type :application.event/created
                :event/actor "alice"
                :event/time (DateTime. 1000)
                :application/id (next-expected-app-id)
                :application/external-id "1970/4"
                :application/resources [{:catalogue-item/id cat-id2
                                         :resource/ext-id "res1"}]
                :application/licenses [{:license/id lic-id}]
                :form/id form-id
                :workflow/id wf-id2
                :workflow/type :workflow/dynamic}
               (application-created-event! {:catalogue-item-ids [cat-id2]
                                            :time (DateTime. 1000)
                                            :actor "alice"}
                                           applications/db-injections)))))))

(deftest test-application-external-id!
  (is (= [] (db/get-external-ids {:prefix "1981"})))
  (is (= [] (db/get-external-ids {:prefix "1980"})))
  (is (= "1981/1" (application-external-id! (DateTime. #inst "1981-03-02"))))
  (is (= "1981/2" (application-external-id! (DateTime. #inst "1981-01-01"))))
  (is (= "1981/3" (application-external-id! (DateTime. #inst "1981-04-03"))))
  (is (= "1980/1" (application-external-id! (DateTime. #inst "1980-12-12"))))
  (is (= "1980/2" (application-external-id! (DateTime. #inst "1980-12-12"))))
  (is (= "1981/4" (application-external-id! (DateTime. #inst "1981-04-01")))))

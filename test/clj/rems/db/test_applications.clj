(ns ^:integration rems.db.test-applications
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [clojure.test.check.generators :as generators]
            [rems.application.events :as events]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.events :as db-events]
            [rems.db.test-data :as test-data]
            [rems.db.testing :refer [test-db-fixture rollback-db-fixture test-data-fixture]]
            [rems.util :refer [try-catch-ex]]
            [schema-generators.generators :as sg])
  (:import [clojure.lang ExceptionInfo]
           [java.util UUID]
           [org.joda.time DateTime DateTimeZone]))

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
    (is (thrown-with-msg? ExceptionInfo #"Value cannot be coerced to match schema"
                          (db-events/json->event "{}"))))

  (testing "json data format"
    (let [event {:event/type :application.event/submitted
                 :event/time (DateTime. 2020 1 1 12 0 0 (DateTimeZone/forID "Europe/Helsinki"))
                 :event/actor "foo"
                 :application/id 123}
          json (db-events/event->json event)]
      (is (str/includes? json "\"event/time\":\"2020-01-01T10:00:00.000Z\"")))))

(deftest test-get-catalogue-item-licenses
  (let [form-id (test-data/create-form! {})]
    (testing "resource licenses"
      (let [lic-id (test-data/create-license! {})
            wf-id (test-data/create-workflow! {})
            res-id (test-data/create-resource! {:resource-ext-id (str (UUID/randomUUID))
                                                :license-ids [lic-id]})
            cat-id (test-data/create-catalogue-item! {:resource-id res-id
                                                      :form-id form-id
                                                      :workflow-id wf-id})]
        (is (= [lic-id]
               (map :id (applications/get-catalogue-item-licenses cat-id))))))

    (testing "workflow licenses"
      (let [lic-id (test-data/create-license! {})
            wf-id (test-data/create-workflow! {})
            _ (db/create-workflow-license! {:wfid wf-id :licid lic-id})
            res-id (test-data/create-resource! {:resource-ext-id (str (UUID/randomUUID))})
            cat-id (test-data/create-catalogue-item! {:resource-id res-id
                                                      :form-id form-id
                                                      :workflow-id wf-id})]
        (is (= [lic-id]
               (map :id (applications/get-catalogue-item-licenses cat-id))))))))

(deftest test-application-external-id!
  (let [application-external-id! @#'applications/application-external-id!]
    (is (= [] (db/get-external-ids {:prefix "1981"})))
    (is (= [] (db/get-external-ids {:prefix "1980"})))
    (is (= "1981/1" (application-external-id! (DateTime. #inst "1981-03-02"))))
    (is (= "1981/2" (application-external-id! (DateTime. #inst "1981-01-01"))))
    (is (= "1981/3" (application-external-id! (DateTime. #inst "1981-04-03"))))
    (is (= "1980/1" (application-external-id! (DateTime. #inst "1980-12-12"))))
    (is (= "1980/2" (application-external-id! (DateTime. #inst "1980-12-12"))))
    (is (= "1981/4" (application-external-id! (DateTime. #inst "1981-04-01"))))))

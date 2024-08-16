(ns ^:integration rems.db.test-applications
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [clojure.test.check.generators :as generators]
            [rems.application.events :as events]
            [rems.db.applications :as applications]
            [rems.db.core :as db]
            [rems.db.events :as db-events]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.db.testing :refer [test-db-fixture rollback-db-fixture]]
            [rems.util :refer [try-catch-ex]]
            [schema-generators.generators :as sg])
  (:import [clojure.lang ExceptionInfo]
           [java.util UUID]
           [org.joda.time DateTime DateTimeZone]))

(use-fixtures :once test-db-fixture)
(use-fixtures :each rollback-db-fixture)

(deftest test-event-serialization
  (testing "round trip serialization"
    (let [generators {DateTime (generators/fmap #(DateTime. ^long % DateTimeZone/UTC)
                                                (generators/large-integer* {:min 0}))}
          ;; The default max-size of 100 was too much: ran out of heap
          ;; space when generating DraftSavedEvent
          max-size 30]
      (doseq [event (take 100 (generators/sample-seq (sg/generator events/Event generators) max-size))]
        (is (= event (-> event db-events/event->json db-events/specter-json->event))))))

  (testing "event->json validates events"
    (is (not
         (:rems.event/validate-event
          (try-catch-ex (db-events/event->json {}))))))

  (testing "json->event variants validate events"
    (is (thrown-with-msg? ExceptionInfo #"Value cannot be coerced to match schema"
                          (db-events/schema-json->event "{}")))
    (is (thrown-with-msg? AssertionError #"\QAssert failed: (:event/type event)\E"
                          (db-events/manual-json->event "{}")))
    (is (thrown-with-msg? AssertionError #"\QAssert failed: (:event/time event)\E"
                          (db-events/specter-json->event "{\"event/type\": \"nonexistent\"}")))
    (is (thrown-with-msg? IllegalArgumentException #"Invalid format"
                          (db-events/specter-json->event "{\"event/type\": \"nonexistent\",\"event/time\": \"1-2-3\"}"))))

  (testing "json data format"
    (let [event {:event/type :application.event/submitted
                 :event/time (DateTime. 2020 1 1 12 0 0 (DateTimeZone/forID "Europe/Helsinki"))
                 :event/actor "foo"
                 :application/id 123}
          json (db-events/event->json event)]
      (is (str/includes? json "\"event/time\":\"2020-01-01T10:00:00.000Z\"")))))

(deftest test-get-catalogue-item-licenses
  (let [form-id (test-helpers/create-form! {})]
    (testing "resource licenses"
      (let [lic-id (test-helpers/create-license! {})
            wf-id (test-helpers/create-workflow! {})
            res-id (test-helpers/create-resource! {:resource-ext-id (str (UUID/randomUUID))
                                                   :license-ids [lic-id]})
            cat-id (test-helpers/create-catalogue-item! {:resource-id res-id
                                                         :form-id form-id
                                                         :workflow-id wf-id})]
        (is (= [lic-id]
               (map :license/id (applications/get-catalogue-item-licenses cat-id))))))

    (testing "workflow licenses"
      (let [lic-id (test-helpers/create-license! {})
            wf-id (test-helpers/create-workflow! {:licenses [lic-id]})
            res-id (test-helpers/create-resource! {:resource-ext-id (str (UUID/randomUUID))})
            cat-id (test-helpers/create-catalogue-item! {:resource-id res-id
                                                         :form-id form-id
                                                         :workflow-id wf-id})]
        (is (= [lic-id]
               (map :license/id (applications/get-catalogue-item-licenses cat-id))))))))

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

(deftest test-delete-application!
  (let [_ (test-helpers/create-user! {:userid "applicant1"})
        _ (test-helpers/create-user! {:userid "applicant2"})
        _ (test-helpers/create-user! {:userid "unrelated"})
        app-id1 (test-helpers/create-application! {:actor "applicant1"})
        _ (test-helpers/create-application! {:actor "applicant2"})]
    (is (applications/get-application app-id1))
    (is (= [app-id1] (map :application/id (applications/get-my-applications "applicant1"))))
    (is (= #{:applicant} (applications/get-all-application-roles "applicant1")))
    (is (= #{"applicant1" "applicant2"} (applications/get-users-with-role :applicant)))

    (applications/delete-application! app-id1)

    (testing "application disappears from my applications"
      (is (= [] (applications/get-my-applications "applicant1"))))
    (testing "application disappears from all-applications-cache (apps-by-user)"
      (is (= [] (applications/get-all-applications "applicant1"))))
    (testing "application disappears from all-applications-cache (roles-by-user)"
      (is (= #{} (applications/get-all-application-roles "applicant1"))))
    (testing "application disappears from all-applications-cache (users-by-role)"
      (is (= #{"applicant2"} (applications/get-users-with-role :applicant))))
    (testing "deleted draft is gone"
      (is (not (applications/get-application app-id1))))
    (testing "events are gone from event cache"
      (is (empty? (db-events/get-application-events app-id1))))
    (testing "events are gone from DB"
      (is (empty? (db/get-application-events {:application app-id1}))))
    (testing "db entry for application is gone"
      (is (not (contains? (set (map :id (db/get-application-ids {}))) app-id1)))))

  (testing "with two applications"
    (let [app-id1 (test-helpers/create-application! {:actor "applicant1"})
          app-id2 (test-helpers/create-application! {:actor "applicant1"})]
      (test-helpers/command! {:application-id app-id2
                              :type :application.command/submit
                              :actor "applicant1"})
      (is (applications/get-application app-id1))
      (is (applications/get-application app-id2))
      (is (= [app-id1 app-id2] (sort (map :application/id (applications/get-my-applications "applicant1")))))
      (is (= #{:applicant} (applications/get-all-application-roles "applicant1")))
      (is (= #{"applicant1" "applicant2"} (applications/get-users-with-role :applicant)))

      (applications/delete-application! app-id1)

      (testing "application disappears from my applications"
        (is (= [app-id2] (map :application/id (applications/get-my-applications "applicant1")))))
      (testing "application disappears from all-applications-cache (apps-by-user)"
        (is (= [app-id2] (map :application/id (applications/get-all-applications "applicant1")))))
      (testing "role persists in roles-by-user"
        (is (= #{:applicant} (applications/get-all-application-roles "applicant1"))))
      (testing "role persists in users-by-role"
        (is (= #{"applicant1" "applicant2"} (applications/get-users-with-role :applicant))))
      (testing "deleted draft is gone"
        (is (not (applications/get-application app-id1))))
      (is (applications/get-application app-id2))
      (testing "events are gone from event cache"
        (is (empty? (db-events/get-application-events app-id1))))
      (testing "events are gone from DB"
        (is (empty? (db/get-application-events {:application app-id1}))))
      (testing "db entry for application is gone"
        (is (not (contains? (set (map :id (db/get-application-ids {}))) app-id1))))))

  (let [app-id1 (test-helpers/create-application! {:actor "applicant1"})]
    (test-helpers/command! {:application-id app-id1
                            :type :application.command/submit
                            :actor "applicant1"})
    (testing "can't delete submitted application"
      (is (thrown? AssertionError (applications/delete-application! app-id1))))
    (test-helpers/command! {:application-id app-id1
                            :type :application.command/return
                            :actor "developer"})
    (testing "can't delete returned application"
      (is (thrown? AssertionError (applications/delete-application! app-id1))))))

(deftest test-cache-reload
  (let [_ (test-helpers/create-user! {:userid "applicant1"})
        _ (test-helpers/create-user! {:userid "applicant2"})
        _ (test-helpers/create-user! {:userid "handler"})
        _ (test-helpers/create-user! {:userid "unrelated"})
        workflow (test-helpers/create-workflow! {:handlers ["handler"]})
        catalogue-item (test-helpers/create-catalogue-item! {:workflow-id workflow})
        app-id1 (test-helpers/create-application! {:actor "applicant1" :catalogue-item-ids [catalogue-item]})
        _ (test-helpers/submit-application {:actor "applicant1" :application-id app-id1})]
    (is (applications/get-application app-id1))
    (is (= [app-id1] (map :application/id (applications/get-my-applications "applicant1"))))
    (is (= #{:applicant} (applications/get-all-application-roles "applicant1")))
    (is (= #{"applicant1"} (applications/get-users-with-role :applicant)))
    (is (= #{"handler"} (applications/get-users-with-role :handler)))

    (test-helpers/command! {:type :application.command/add-member
                            :application-id app-id1
                            :member {:userid "applicant2"}
                            :actor "handler"})

    (is (= #{"applicant1"} (applications/get-users-with-role :applicant)))
    (is (= #{"applicant2"} (applications/get-users-with-role :member)))
    (is (= #{"handler"} (applications/get-users-with-role :handler)))

    (test-helpers/command! {:type :application.command/change-applicant
                            :application-id app-id1
                            :member {:userid "applicant2"}
                            :actor "handler"})

    (is (= #{"applicant2"} (applications/get-users-with-role :applicant)))
    (is (= #{"applicant1"} (applications/get-users-with-role :member)))
    (is (= #{"handler"} (applications/get-users-with-role :handler)))

    (test-helpers/command! {:type :application.command/remove-member
                            :application-id app-id1
                            :member {:userid "applicant1"}
                            :actor "handler"})

    (is (= #{"applicant2"} (applications/get-users-with-role :applicant)))
    (is (= #{} (applications/get-users-with-role :member)))
    (is (= #{"handler"} (applications/get-users-with-role :handler)))

    (testing "application disappears from my applications"
      (is (= [] (applications/get-my-applications "applicant1"))))
    (testing "application disappears from all-applications-cache (apps-by-user)"
      (is (= [] (applications/get-all-applications "applicant1"))))
    (testing "role disappears from all-applications-cache (roles-by-user)"
      (is (= #{} (applications/get-all-application-roles "applicant1"))))
    (testing "applicant1 disappears from all-applications-cache (users-by-role)"
      (is (= #{"applicant2"} (applications/get-users-with-role :applicant))))
    (testing "applicant1 disappears from all-applications-cache (users-by-role)"
      (is (= #{} (applications/get-users-with-role :member))))
    (testing "db entry for application is not gone"
      (is (contains? (set (map :id (db/get-application-ids {}))) app-id1)))))

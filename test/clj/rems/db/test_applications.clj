(ns ^:integration rems.db.test-applications
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [clojure.test.check.generators :as generators]
            [luminus-migrations.core :as migrations]
            [mount.core :as mount]
            [rems.config :refer [env]]
            [rems.db.applications :refer :all]
            [rems.db.catalogue :as catalogue]
            [rems.db.core :as db]
            [rems.db.form :as form]
            [rems.db.resource :as resource]
            [rems.db.test-data :as test-data]
            [rems.db.workflow :as workflow]
            [rems.workflow.dynamic :as dynamic]
            [schema-generators.generators :as sg])
  (:import (org.joda.time DateTime DateTimeZone)
           (clojure.lang ExceptionInfo)))

(use-fixtures
  :once
  (fn [f]
    (mount/start
     #'rems.config/env
     #'rems.db.core/*db*)
    (db/assert-test-database!)
    (migrations/migrate ["reset"] (select-keys env [:database-url]))
    (test-data/create-test-data!)
    (f)
    (mount/stop)))

(deftest can-act-as?-test
  (is (can-act-as? "developer" (get-application-state 10) "approver"))
  (is (not (can-act-as? "developer" (get-application-state 10) "reviewer")))
  (is (not (can-act-as? "alice" (get-application-state 10) "approver"))))

(deftest test-handling-event?
  (are [en] (handling-event? nil {:event en})
    "approve"
    "autoapprove"
    "reject"
    "return"
    "review")
  (is (not (handling-event? nil {:event "apply"})))
  (is (not (handling-event? nil {:event "withdraw"})))
  (is (handling-event? {:applicantuserid 123} {:event "close" :userid 456}))
  (is (not (handling-event? {:applicantuserid 123} {:event "close" :userid 123}))
      "applicant's own close is not handler's action"))

(deftest test-handled?
  (is (not (handled? nil)))
  (is (handled? {:state "approved"}))
  (is (handled? {:state "rejected"}))
  (is (handled? {:state "returned"}))
  (is (not (handled? {:state "closed"})))
  (is (not (handled? {:state "withdrawn"})))
  (is (handled? {:state "approved" :events [{:event "approve"}]}))
  (is (handled? {:state "rejected" :events [{:event "reject"}]}))
  (is (handled? {:state "returned" :events [{:event "apply"} {:event "return"}]}))
  (is (not (handled? {:state "closed"
                      :events [{:event "apply"}
                               {:event "close"}]}))
      "applicant's own close is not handled by others")
  (is (not (handled? {:state "withdrawn"
                      :events [{:event "apply"}
                               {:event "withdraw"}]}))
      "applicant's own withdraw is not handled by others")
  (is (handled? {:state "closed" :applicantuserid 123
                 :events [{:event "apply" :userid 123}
                          {:event "return" :userid 456}
                          {:event "close" :userid 123}]})
      "previously handled (returned) is still handled if closed by the applicant")
  (is (not (handled? {:state "closed" :applicantuserid 123
                      :events [{:event "apply" :userid 123}
                               {:event "withdraw" :userid 123}
                               {:event "close" :userid 123}]}))
      "actions only by applicant"))

(deftest test-event-serialization
  (testing "round trip serialization"
    (let [generators {DateTime (generators/fmap #(DateTime. ^long % DateTimeZone/UTC)
                                                (generators/large-integer* {:min 0}))}]
      (doseq [event (sg/sample 100 dynamic/Event generators)]
        (is (= event (-> event event->json json->event))))))

  (testing "event->json validates events"
    (is (thrown-with-msg? ExceptionInfo #"Value does not match schema" (event->json {}))))

  (testing "json->event validates events"
    (is (thrown-with-msg? ExceptionInfo #"Value does not match schema" (json->event "{}"))))

  (testing "json data format"
    (let [event {:event/type :application.event/submitted
                 :event/time (DateTime. 2020 1 1 12 0 0 (DateTimeZone/forID "Europe/Helsinki"))
                 :event/actor "foo"
                 :application/id 123}
          json (event->json event)]
      (is (str/includes? json "\"event/time\":\"2020-01-01T10:00:00.000Z\"")))))

(deftest test-application-created-event
  (let [form-id (:id (form/create-form! "owner" {:organization "abc"
                                                 :title ""
                                                 :items []}))
        res-id (:id (resource/create-resource! {:resid "res1"
                                                :organization "abc"
                                                :licenses []}
                                               "owner"))
        wf-id (:id (workflow/create-workflow! {:type :dynamic
                                               :organization "abc"
                                               :title ""
                                               :handlers []
                                               :user-id "owner"}))
        cat-id (:id (catalogue/create-catalogue-item! {:title ""
                                                       :form form-id
                                                       :resid res-id
                                                       :wfid wf-id}))]

    (testing "minimal application"
      (is (= {:event/type :application.event/created
              :event/actor "alice"
              :event/time (DateTime. 1000)
              :application/id 42
              :application/resources [{:catalogue-item/id cat-id
                                       :resource/ext-id "res1"}]
              :application/licenses []
              :form/id form-id
              :workflow/id wf-id
              :workflow/type :workflow/dynamic
              :workflow.dynamic/handlers #{}}
             (application-created-event {:application-id 42
                                         :catalogue-item-ids [cat-id]
                                         :time (DateTime. 1000)
                                         :actor "alice"}))))

    (testing "multiple catalogue items") ; TODO

    (testing "error: zero catalogue items") ; TODO

    (testing "error: non-existing catalogue items") ; TODO

    (testing "error: catalogue items with different forms") ; TODO

    (testing "error: catalogue items with different workflows") ; TODO

    (testing "resource licenses") ; TODO

    (testing "workflow licenses") ; TODO

    (testing "workflow handlers"))) ; TODO

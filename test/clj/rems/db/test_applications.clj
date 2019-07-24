(ns ^:integration rems.db.test-applications
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [clojure.test.check.generators :as generators]
            [rems.application.events :as events]
            [rems.db.applications :refer [application-created-event application-external-id!]]
            [rems.db.catalogue :as catalogue]
            [rems.db.core :as db]
            [rems.db.events :as db-events]
            [rems.db.form :as form]
            [rems.db.licenses :as licenses]
            [rems.db.resource :as resource]
            [rems.db.testing :refer [test-db-fixture rollback-db-fixture test-data-fixture]]
            [rems.db.workflow :as workflow]
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

(deftest test-application-created-event
  (let [wf-id (:id (workflow/create-workflow! {:type :dynamic
                                               :organization "abc"
                                               :title ""
                                               :handlers []
                                               :user-id "owner"}))
        _ (assert wf-id)
        form-id (:id (form/create-form! "owner" {:form/organization "abc"
                                                 :form/title ""
                                                 :form/fields []}))
        _ (assert form-id)
        res-id (:id (resource/create-resource! {:resid "res1"
                                                :organization "abc"
                                                :licenses []}
                                               "owner"))
        _ (assert res-id)
        cat-id (:id (catalogue/create-catalogue-item! {:title ""
                                                       :resid res-id
                                                       :form form-id
                                                       :wfid wf-id}))
        _ (assert cat-id)]

    (testing "minimal application"
      (is (= {:event/type :application.event/created
              :event/actor "alice"
              :event/time (DateTime. 1000)
              :application/id 42
              :application/external-id nil
              :application/resources [{:catalogue-item/id cat-id
                                       :resource/ext-id "res1"}]
              :application/licenses []
              :form/id form-id
              :workflow/id wf-id
              :workflow/type :workflow/dynamic}
             (application-created-event {:application-id 42
                                         :catalogue-item-ids [cat-id]
                                         :time (DateTime. 1000)
                                         :actor "alice"}))))

    (testing "multiple resources"
      (let [res-id2 (:id (resource/create-resource! {:resid "res2"
                                                     :organization "abc"
                                                     :licenses []}
                                                    "owner"))
            _ (assert res-id2)
            cat-id2 (:id (catalogue/create-catalogue-item! {:title ""
                                                            :resid res-id2
                                                            :form form-id
                                                            :wfid wf-id}))
            _ (assert cat-id2)]
        (is (= {:event/type :application.event/created
                :event/actor "alice"
                :event/time (DateTime. 1000)
                :application/id 42
                :application/external-id nil
                :application/resources [{:catalogue-item/id cat-id
                                         :resource/ext-id "res1"}
                                        {:catalogue-item/id cat-id2
                                         :resource/ext-id "res2"}]
                :application/licenses []
                :form/id form-id
                :workflow/id wf-id
                :workflow/type :workflow/dynamic}
               (application-created-event {:application-id 42
                                           :catalogue-item-ids [cat-id cat-id2]
                                           :time (DateTime. 1000)
                                           :actor "alice"})))))

    (testing "error: zero catalogue items"
      (is (thrown-with-msg? AssertionError #"catalogue item not specified"
                            (application-created-event {:application-id 42
                                                        :catalogue-item-ids []
                                                        :time (DateTime. 1000)
                                                        :actor "alice"}))))

    (testing "error: non-existing catalogue items"
      (is (thrown-with-msg? AssertionError #"catalogue item not found"
                            (application-created-event {:application-id 42
                                                        :catalogue-item-ids [999999]
                                                        :time (DateTime. 1000)
                                                        :actor "alice"}))))

    (testing "error: catalogue items with different forms"
      (let [form-id2 (:id (form/create-form! "owner" {:form/organization "abc"
                                                      :form/title ""
                                                      :form/fields []}))
            _ (assert form-id2)
            res-id2 (:id (resource/create-resource! {:resid "res2+"
                                                     :organization "abc"
                                                     :licenses []}
                                                    "owner"))
            _ (assert res-id2)
            cat-id2 (:id (catalogue/create-catalogue-item! {:title ""
                                                            :resid res-id2
                                                            :form form-id2
                                                            :wfid wf-id}))
            _ (assert cat-id2)]
        (is (thrown-with-msg? AssertionError #"catalogue items did not have the same form"
                              (application-created-event {:application-id 42
                                                          :catalogue-item-ids [cat-id cat-id2]
                                                          :time (DateTime. 1000)
                                                          :actor "alice"})))))

    (testing "error: catalogue items with different workflows"
      (let [wf-id2 (:id (workflow/create-workflow! {:type :dynamic
                                                    :organization "abc"
                                                    :title ""
                                                    :handlers []
                                                    :user-id "owner"}))
            _ (assert wf-id2)
            res-id2 (:id (resource/create-resource! {:resid "res2++"
                                                     :organization "abc"
                                                     :licenses []}
                                                    "owner"))
            _ (assert res-id2)
            cat-id2 (:id (catalogue/create-catalogue-item! {:title ""
                                                            :resid res-id2
                                                            :form form-id
                                                            :wfid wf-id2}))
            _ (assert cat-id2)]
        (is (thrown-with-msg? AssertionError #"catalogue items did not have the same workflow"
                              (application-created-event {:application-id 42
                                                          :catalogue-item-ids [cat-id cat-id2]
                                                          :time (DateTime. 1000)
                                                          :actor "alice"})))))

    (testing "resource licenses"
      (let [lic-id (:id (licenses/create-license! {:licensetype "text"
                                                   :title ""
                                                   :textcontent ""
                                                   :localizations {}}
                                                  "owner"))
            _ (assert lic-id)
            res-id2 (:id (resource/create-resource! {:resid "res2+++"
                                                     :organization "abc"
                                                     :licenses [lic-id]}
                                                    "owner"))
            _ (assert res-id2)
            cat-id2 (:id (catalogue/create-catalogue-item! {:title ""
                                                            :resid res-id2
                                                            :form form-id
                                                            :wfid wf-id}))
            _ (assert cat-id2)]
        (is (= {:event/type :application.event/created
                :event/actor "alice"
                :event/time (DateTime. 1000)
                :application/id 42
                :application/external-id nil
                :application/resources [{:catalogue-item/id cat-id2
                                         :resource/ext-id "res2+++"}]
                :application/licenses [{:license/id lic-id}]
                :form/id form-id
                :workflow/id wf-id
                :workflow/type :workflow/dynamic}
               (application-created-event {:application-id 42
                                           :catalogue-item-ids [cat-id2]
                                           :time (DateTime. 1000)
                                           :actor "alice"})))))

    (testing "workflow licenses"
      (let [lic-id (:id (licenses/create-license! {:licensetype "text"
                                                   :title ""
                                                   :textcontent ""
                                                   :localizations {}}
                                                  "owner"))
            _ (assert lic-id)
            wf-id2 (:id (workflow/create-workflow! {:type :dynamic
                                                    :organization "abc"
                                                    :title ""
                                                    :handlers []
                                                    :user-id "owner"}))
            _ (assert wf-id2)
            _ (db/create-workflow-license! {:wfid wf-id2 :licid lic-id})
            cat-id2 (:id (catalogue/create-catalogue-item! {:title ""
                                                            :resid res-id
                                                            :form form-id
                                                            :wfid wf-id2}))
            _ (assert cat-id2)]
        (is (= {:event/type :application.event/created
                :event/actor "alice"
                :event/time (DateTime. 1000)
                :application/id 42
                :application/external-id nil
                :application/resources [{:catalogue-item/id cat-id2
                                         :resource/ext-id "res1"}]
                :application/licenses [{:license/id lic-id}]
                :form/id form-id
                :workflow/id wf-id2
                :workflow/type :workflow/dynamic}
               (application-created-event {:application-id 42
                                           :catalogue-item-ids [cat-id2]
                                           :time (DateTime. 1000)
                                           :actor "alice"})))))))

(deftest test-application-external-id!
  (is (= [] (db/get-external-ids {:prefix "1981"})))
  (is (= [] (db/get-external-ids {:prefix "1980"})))
  (is (= "1981/1" (application-external-id! (DateTime. #inst "1981-03-02"))))
  (is (= "1981/2" (application-external-id! (DateTime. #inst "1981-01-01"))))
  (is (= "1981/3" (application-external-id! (DateTime. #inst "1981-04-03"))))
  (is (= "1980/1" (application-external-id! (DateTime. #inst "1980-12-12"))))
  (is (= "1980/2" (application-external-id! (DateTime. #inst "1980-12-12"))))
  (is (= "1981/4" (application-external-id! (DateTime. #inst "1981-04-01")))))

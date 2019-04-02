(ns ^:integration rems.api.test-administration
  "Tests for invariants across administration APIs."
  (:require [clojure.test :refer :all]
            [rems.handler :refer [handler]]
            [rems.api.testing :refer :all]))

(use-fixtures
  :once
  api-fixture)

(def api-key "42")
(def user-id "owner")


(deftest test-archiving-disabling
  (let [api-key "42"
        user-id "owner"
        resource-id (:id (api-call :post "/api/resources/create" {:resid "test" :organization "abc" :licenses []}
                                   api-key user-id))
        form-id (:id (api-call :post "/api/forms/create" {:organization "abc" :title "form update test" :items []}
                               api-key user-id))
        workflow-id (:id (api-call :post "/api/workflows/create"
                                   {:organization "abc" :title "dynamic workflow"
                                    :type :dynamic :handlers [user-id]}
                                   api-key user-id))
        catalogue-id (:id (api-call :post "/api/catalogue-items/create"
                                    {:title "test-item-title"
                                     :form form-id
                                     :resid resource-id
                                     :wfid workflow-id}
                                    api-key user-id))]
    (is resource-id)
    (is form-id)
    (is workflow-id)
    (is catalogue-id)

    (testing "can't archive resource if it is part of an active catalogue item"
      (let [resp (api-call :put "/api/resources/update" {:id resource-id :enabled true :archived true}
                           api-key user-id)]
        (is (false? (:success resp)))
        (is (= [{:type "t.administration.errors/resource-in-use" :catalogue-items [catalogue-id]}]
               (:errors resp)))))
    (testing "can't archive a form that's in use"
      (let [resp (api-call :put "/api/forms/update" {:id form-id :enabled true :archived true}
                           api-key user-id)]
        (is (false? (:success resp)))
        (is (= [{:type "t.administration.errors/form-in-use" :catalogue-items [catalogue-id]}]
               (:errors resp)))))
    (testing "can't archive a workflow that's in use"
      (let [resp (api-call :put "/api/workflows/update" {:id workflow-id :enabled true :archived true}
                           api-key user-id)]
        (is (false? (:success resp)))
        (is (= [{:type "t.administration.errors/workflow-in-use" :catalogue-items [catalogue-id]}]
               (:errors resp)))))

    (testing "can archive a catalogue item"
      (is (:success (api-call :put "/api/catalogue-items/update" {:id catalogue-id :enabled true :archived true}
                              api-key user-id))))
    (testing "can archive a resource that's not in use"
      (is (:success (api-call :put "/api/resources/update" {:id resource-id :enabled true :archived true}
                              api-key user-id))))
    (testing "can archive a form that's not in use"
      (is (:success (api-call :put "/api/forms/update" {:id form-id :enabled true :archived true}
                              api-key user-id))))
    (testing "can archive a workflow that's not in use"
      (is (:success (api-call :put "/api/workflows/update" {:id workflow-id :enabled true :archived true}
                              api-key user-id))))))

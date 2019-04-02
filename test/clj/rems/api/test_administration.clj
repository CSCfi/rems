(ns ^:integration rems.api.test-administration
  "Tests for invariants across administration APIs."
  (:require [clojure.test :refer :all]
            [rems.handler :refer [handler]]
            [rems.api.testing :refer :all]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture)

(deftest test-archiving
  (let [api-key "42"
        user-id "owner"
        resource-id (-> (request :post "/api/resources/create")
                        (authenticate api-key user-id)
                        (json-body {:resid "test" :organization "abc" :licenses []})
                        handler
                        read-ok-body
                        :id)
        form-id (-> (request :post "/api/forms/create")
                    (authenticate api-key user-id)
                    (json-body {:organization "abc" :title "form update test"
                                :items []})
                    handler
                    read-ok-body
                    :id)
        workflow-id (-> (request :post "/api/workflows/create")
                        (json-body {:organization "abc" :title "dynamic workflow"
                                    :type :dynamic :handlers [user-id]})
                   (authenticate api-key user-id)
                   handler
                   read-ok-body
                   :id)
        catalogue-id (-> (request :post "/api/catalogue-items/create")
                         (authenticate api-key "owner")
                         (json-body {:title "test-item-title"
                                     :form form-id
                                     :resid resource-id
                                     :wfid workflow-id})
                         handler
                         read-ok-body
                         :id)]
    (is resource-id)
    (is form-id)
    (is workflow-id)
    (is catalogue-id)

    (testing "can't archive resource if it is part of an active catalogue item"
      (let [resp (-> (request :put "/api/resources/update")
                     (authenticate api-key user-id)
                     (json-body {:id resource-id :enabled true :archived true})
                     handler
                     read-ok-body)]
        (is (false? (:success resp)))
        (is (= [{:type "t.administration.errors/resource-in-use" :catalogue-items [catalogue-id]}]
               (:errors resp)))))
    (testing "can't archive a form that's in use"
      (let [resp (-> (request :put "/api/forms/update")
                     (authenticate api-key user-id)
                     (json-body {:id form-id :enabled true :archived true})
                     handler
                     read-ok-body)]
        (is (false? (:success resp)))
        (is (= [{:type "t.administration.errors/form-in-use" :catalogue-items [catalogue-id]}]
               (:errors resp)))))
    (testing "can't archive a workflow that's in use"
      (let [resp (-> (request :put "/api/workflows/update")
                     (authenticate api-key user-id)
                     (json-body {:id workflow-id :enabled true :archived true})
                     handler
                     read-ok-body)]
        (is (false? (:success resp)))
        (is (= [{:type "t.administration.errors/workflow-in-use" :catalogue-items [catalogue-id]}]
               (:errors resp)))))

    (testing "can archive a catalogue item"
      (is (-> (request :put "/api/catalogue-items/update")
              (authenticate api-key user-id)
              (json-body {:id catalogue-id :enabled true :archived true})
              handler
              read-ok-body
              :success)))
    (testing "can archive a resource that's not in use"
      (is (-> (request :put "/api/resources/update")
              (authenticate api-key user-id)
              (json-body {:id resource-id :enabled true :archived true})
              handler
              read-ok-body
              :success)))
    (testing "can archive a form that's not in use"
      (is (-> (request :put "/api/forms/update")
              (authenticate api-key user-id)
              (json-body {:id form-id :enabled true :archived true})
              handler
              read-ok-body
              :success)))
    (testing "can archive a workflow that's not in use"
      (is (-> (request :put "/api/workflows/update")
              (authenticate api-key user-id)
              (json-body {:id workflow-id :enabled true :archived true})
              handler
              read-ok-body
              :success)))))

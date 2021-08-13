(ns ^:integration rems.api.test-invitations
  (:require [clojure.test :refer :all]
            [rems.api.testing :refer :all]
            [rems.db.applications]
            [rems.db.test-data :as test-data]
            [rems.db.test-data-helpers :as test-helpers]
            [rems.json]
            [ring.mock.request :refer :all]))

(use-fixtures
  :each
  api-fixture
  ;; TODO should this fixture have a name?
  (fn [f]
    (test-data/create-test-api-key!)
    (test-data/create-test-users-and-roles!)
    (f)))

(deftest test-invitation-flow
  (let [workflow-id (test-helpers/create-workflow! {})]
    (testing "before invitation"
      (is (= [] (api-call :get "/api/invitations" nil test-data/+test-api-key+ "owner"))))

    (testing "create invitation"
      (let [body (api-call :post "/api/invitations/create" {:email "jane.smythe@test.org"
                                                            :workflow-id workflow-id}
                           test-data/+test-api-key+ "owner")]
        (is (= {:success true}
               (dissoc body :id)))))

    (testing "after invitation"
      (is (= [{:invitation/email "jane.smythe@test.org"
               :invitation/invited-by {:email "owner@example.com"
                                       :userid "owner"
                                       :name "Owner"}
               :invitation/workflow {:workflow/id workflow-id}}]
             (->> (api-call :get "/api/invitations" nil test-data/+test-api-key+ "owner")
                  (mapv #(dissoc %
                                 :invitation/id ; always different from sequence
                                 :invitation/created))))))))  ; should be checked in service

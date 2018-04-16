(ns ^:integration rems.test.api.actions
  (:require [clojure.test :refer :all]
            [rems.handler :refer [app]]
            [rems.test.api :refer :all]
            [rems.test.tempura :refer [fake-tempura-fixture]]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  fake-tempura-fixture
  api-fixture)

(deftest actions-api-test
  (let [api-key "42"
        user-id "developer"]
    (let [response (-> (request :get (str "/api/actions"))
                       (authenticate api-key user-id)
                       app)
          cmd-response (read-body response)]
      (testing "listing"
        (is (:approver? cmd-response))
        (is (= [2 8] (map :id (:approvals cmd-response))))
        (is (= [3 4 5 7 9] (map :id (:handled-approvals cmd-response))))
        (is (empty? (:reviews cmd-response)))
        (is (empty? (:handled-reviews cmd-response)))))))

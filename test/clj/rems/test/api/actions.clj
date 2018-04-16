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
          body (read-body response)]
      (testing "listing"
        (is (:approver? body))
        (is (= [2 8] (map :id (:approvals body))))
        (is (= [2 2 3] (sort (map :id (mapcat :catalogue-items (:approvals body))))))
        (is (= [3 4 5 7 9] (map :id (:handled-approvals body))))
        (is (empty? (:reviews body)))
        (is (empty? (:handled-reviews body)))))))

(deftest actions-api-security-test
  (testing "listing without authentication"
    (let [response (-> (request :get (str "/api/actions"))
                       app)
          body (read-body response)]
      (is (= "unauthorized" body)))))

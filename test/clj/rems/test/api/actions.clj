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

(defn get-response-body [user-id]
  (let [api-key "42"
        response (-> (request :get (str "/api/actions"))
                     (authenticate api-key user-id)
                     app)]
    (read-body response)))

(deftest actions-api-test
  (testing "listing"
    (let [body (get-response-body "developer")]
      (is (:approver? body))
      (is (= [2 8] (map :id (:approvals body))))
      (is (= [2 2 3] (sort (map :id (mapcat :catalogue-items (:approvals body))))))
      (is (= [3 4 5 7 9] (map :id (:handled-approvals body))))
      (is (empty? (:reviews body)))
      (is (empty? (:handled-reviews body)))))
  (testing "listing as another approver"
    (let [body (get-response-body "bob")]
      (is (:approver? body))
      (is (= [2 8] (map :id (:approvals body))))
      (is (= [2 2 3] (sort (map :id (mapcat :catalogue-items (:approvals body))))))
      (is (= [3 4 5 7] (map :id (:handled-approvals body))))
      (is (empty? (:reviews body)))
      (is (empty? (:handled-reviews body)))))
  (testing "listing as a reviewer"
    (let [body (get-response-body "carl")]
      (is (:reviewer? body))
      (is (empty? (:approvals body)))
      (is (empty? (:handled-approvals body)))
      (is (empty? (:reviews body)))
      (is (= [9] (map :id (:handled-reviews body)))))))

(deftest actions-api-security-test
  (testing "listing without authentication"
    (let [response (-> (request :get (str "/api/actions"))
                       app)
          body (read-body response)]
      (is (= "unauthorized" body)))))

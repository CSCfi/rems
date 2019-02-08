(ns ^:integration rems.test.api.actions
  (:require [clojure.test :refer :all]
            [rems.handler :refer [app]]
            [rems.test.api :refer :all]
            [ring.mock.request :refer :all]))

(use-fixtures
  :once
  api-fixture)

(defn get-response-body [user-id endpoint]
  (let [api-key "42"
        response (-> (request :get (str "/api/" endpoint))
                     (authenticate api-key user-id)
                     app)]
    (read-body response)))

(deftest actions-api-test
  (testing "listing"
    (let [actions-body (get-response-body "developer" "actions")
          handled-body (get-response-body "developer" "actions/handled")]
      (is (:approver? actions-body))
      (is (:approver? handled-body))
      (is (= [2 8 11 12 13 15 16 18] (map :id (:approvals actions-body))))
      (is (= [2 2 2 3 7 8 9 9 9] (sort (map :id (mapcat :catalogue-items (:approvals actions-body))))))
      (is (= [3 4 5 7 9 14] (map :id (:handled-approvals handled-body))))
      (is (empty? (:reviews body)))
      (is (empty? (:handled-reviews handled-body)))))
  (testing "listing as another approver"
    (let [actions-body (get-response-body "bob" "actions")
          handled-body (get-response-body "bob" "actions/handled")]
      (is (:approver? actions-body))
      (is (:approver? handled-body))
      (is (= [2 8 11 12] (map :id (:approvals actions-body))))
      (is (= [2 2 3 7 8] (sort (map :id (mapcat :catalogue-items (:approvals actions-body))))))
      (is (= [3 4 5 7] (map :id (:handled-approvals handled-body))))
      (is (empty? (:reviews body)))
      (is (empty? (:handled-reviews handled-body)))))
  (testing "listing as a reviewer"
    (let [actions-body (get-response-body "carl" "actions")
          handled-body (get-response-body "carl" "actions/handled")]
      (is (:reviewer? actions-body))
      (is (:reviewer? handled-body))
      (is (empty? (:approvals body)))
      (is (= [14] (map :id (:handled-approvals handled-body)))) ; TODO should have only one handled
      (is (empty? (:reviews body)))
      (is (= [9 14] (map :id (:handled-reviews handled-body)))))))

(deftest actions-api-security-test
  (testing "listing actions without authentication"
    (let [response (-> (request :get (str "/api/actions"))
                       app)
          body (read-body response)]
      (is (= "unauthorized" body))))
  (testing "listing handled actions without authentication"
    (let [response (-> (request :get (str "/api/actions/handled"))
                       app)
          body (read-body response)]
      (is (= "unauthorized" body)))))

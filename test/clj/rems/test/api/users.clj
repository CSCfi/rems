(ns ^:integration rems.test.api.users
  (:require [clojure.test :refer :all]
            [rems.handler :refer [app]]
            [rems.test.api :refer :all]
            [ring.mock.request :refer :all]))

(def new-user {:eppn "david"
               :mail "d@av.id"
               :commonName "David Newuser"})

(use-fixtures
  :once
  api-fixture)

(deftest users-api-test
  (testing "create"
    (let [response (-> (request :post (str "/api/users/create"))
                       (json-body new-user)
                       (authenticate "42" "owner")
                       app)]
      (is (response-is-ok? response)))))

(deftest workflows-api-security-test
  (testing "without authentication"
    (testing "create"
      (let [response (-> (request :post (str "/api/users/create"))
                         (json-body new-user)
                         app)]
        (is (= 403 (:status response)))
        (is (= "<h1>Invalid anti-forgery token</h1>" (read-body response))))))

  (testing "without owner role"
    (testing "create"
      (let [response (-> (request :post (str "/api/users/create"))
                         (json-body new-user)
                         (authenticate "42" "alice")
                         app)]
        (is (= 401 (:status response)))
        (is (= "unauthorized" (read-body response)))))))
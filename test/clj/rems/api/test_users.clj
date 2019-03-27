(ns ^:integration rems.api.test-users
  (:require [clojure.test :refer :all]
            [rems.db.users :as users]
            [rems.handler :refer [handler]]
            [rems.api.testing :refer :all]
            [ring.mock.request :refer :all]))

(def new-user
  {:eppn "david"
   :mail "d@av.id"
   :commonName "David Newuser"})

(use-fixtures :once api-once-fixture)
(use-fixtures :each api-each-fixture)

(deftest users-api-test
  (testing "create"
    (is (= nil (users/get-user-attributes "david")))
    (-> (request :post (str "/api/users/create"))
        (json-body new-user)
        (authenticate "42" "owner")
        (@handler)
        assert-response-is-ok)
    (is (= {:eppn "david"
            :mail "d@av.id"
            :commonName "David Newuser"} (users/get-user-attributes "david")))))

(deftest workflows-api-security-test
  (testing "without authentication"
    (testing "create"
      (let [response (-> (request :post (str "/api/users/create"))
                         (json-body new-user)
                         (@handler))]
        (is (response-is-unauthorized? response))
        (is (= "Invalid anti-forgery token" (read-body response))))))

  (testing "without owner role"
    (testing "create"
      (let [response (-> (request :post (str "/api/users/create"))
                         (json-body new-user)
                         (authenticate "42" "alice")
                         (@handler))]
        (is (response-is-forbidden? response))
        (is (= "forbidden" (read-body response)))))))

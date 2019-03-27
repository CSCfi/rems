(ns ^:integration rems.api.test-public
  (:require [clojure.test :refer :all]
            [rems.db.roles :as roles]
            [rems.db.users :as users]
            [rems.handler :refer :all]
            [rems.api.testing :refer :all]
            [ring.mock.request :refer :all]))

(use-fixtures :once api-once-fixture)
(use-fixtures :each api-each-fixture)

(deftest service-translations-test
  (let [api-key "42"
        user-id "alice"]
    (let [data (-> (request :get "/api/translations")
                   (authenticate api-key user-id)
                   handler
                   read-body)
          languages (keys data)]
      (is (= [:en :fi] (sort languages))))))

(deftest entitlements-test
  (let [api-key "42"]
    (testing "all"
      (let [data (-> (request :get "/api/entitlements")
                     (authenticate api-key "developer")
                     handler
                     read-body)]
        (is (= 2 (count data)))))

    (testing "just for alice"
      (let [data (-> (request :get "/api/entitlements?user=alice")
                     (authenticate api-key "developer")
                     handler
                     read-body)]
        (is (= 1 (count data)))))

    (testing "listing as applicant"
      (testing "with entitlements"
        (let [body (-> (request :get (str "/api/entitlements"))
                       (authenticate api-key "alice")
                       handler
                       assert-response-is-ok
                       read-body)]
          (is (coll-is-not-empty? body))
          (is (every? #(= (:mail %) "alice@example.com") body))))

      (testing "without entitlements"
        (users/add-user! "allison" {})
        (let [body (-> (request :get (str "/api/entitlements"))
                       (authenticate api-key "allison")
                       handler
                       assert-response-is-ok
                       read-body)]
          (is (coll-is-empty? body)))))))

(ns ^:integration rems.test.api.public
  (:require [clojure.test :refer :all]
            [rems.db.roles :as roles]
            [rems.db.users :as users]
            [rems.handler :refer :all]
            [rems.test.api :refer :all]
            [ring.mock.request :refer :all]))

(use-fixtures
 :once
  api-fixture)

(deftest service-translations-test
  (let [api-key "42"
        user-id "alice"]
    (let [data (-> (request :get "/api/translations")
                   (authenticate api-key user-id)
                   app
                   read-body)
          languages (keys data)]
      (is (= [:en :fi] (sort languages))))))

(deftest entitlements-test
  (let [api-key "42"]
    (testing "all"
      (let [data (-> (request :get "/api/entitlements")
                     (authenticate api-key "developer")
                     app
                     read-body)]
        (is (= 3 (count data)))))

    (testing "just for alice"
      (let [data (-> (request :get "/api/entitlements?user=alice")
                     (authenticate api-key "developer")
                     app
                     read-body)]
        (is (= 1 (count data)))))

    (testing "listing as applicant"
      (testing "with entitlements"
        (let [response (-> (request :get (str "/api/entitlements"))
                           (authenticate api-key "alice")
                           app)
              body (read-body response)]
          (is (response-is-ok? response))
          (is (coll-is-not-empty? body))
          (is (every? #(= (:mail %) "a@li.ce") body))))

      (testing "without entitlements"
        (users/add-user! "allison" {})
        (roles/add-role! "allison" :applicant)
        (let [response (-> (request :get (str "/api/entitlements"))
                           (authenticate api-key "allison")
                           app)
              body (read-body response)]
          (is (response-is-ok? response))
          (is (coll-is-empty? body)))))))
